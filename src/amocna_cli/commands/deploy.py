from __future__ import annotations

import base64
import subprocess
import sys
from typing import Optional
from typing_extensions import Annotated
import typer

from amocna_cli.config import ProjectConfig
from amocna_cli.utils.ui import console, header, info, warn, error, run
from amocna_cli.utils.shell import (
    k8s_apply_manifest,
    k8s_apply_stdin,
    k8s_apply_kustomization,
    k8s_create_secret_generic,
    k8s_create_configmap,
    k8s_rollout_status,
    k8s_wait_ready
)

app = typer.Typer()

def _kubectl_apply_stdin(manifest_yaml: str, dry_run: bool = False) -> None:
    subprocess.run(
        k8s_apply_stdin(dry_run=dry_run),
        input=manifest_yaml,
        text=True,
        check=True,
    )

def _deploy_graphdb(cfg: ProjectConfig, dry_run: bool = False) -> None:
    """Deploy GraphDB to K8s."""
    graphdb_dir = cfg.project_root / "infra" / "graphdb"
    ontology_dir = cfg.project_root / "libs" / "ontology"

    header("Deploying GraphDB")
    
    with console.status("[bold green]Applying GraphDB namespace...[/bold green]"):
        run(k8s_apply_manifest(str(graphdb_dir / "00-namespace.yaml"), dry_run=dry_run))

    license_file = graphdb_dir / "graphdb.license"
    license_bin = graphdb_dir / "graphdb.license.bin"
    if license_file.is_file():
        info("Creating graphdb-license secret...")
        license_bin.write_bytes(base64.b64decode(license_file.read_bytes()))
        try:
            create = subprocess.run(
                k8s_create_secret_generic(
                    name="graphdb-license",
                    from_file_key="GRAPHDB_LICENSE",
                    from_file_val=str(license_bin),
                    namespace="graphdb",
                    dry_run=dry_run
                ),
                capture_output=True,
                text=True,
                check=True,
            )
            _kubectl_apply_stdin(create.stdout, dry_run=dry_run)
        finally:
            license_bin.unlink(missing_ok=True)
    else:
        warn(f"No license found. Place graphdb.license in {graphdb_dir}")

    info("Creating graphdb-ontologies ConfigMap...")
    ontology_files = sorted(ontology_dir.glob("*.rdf"))
    if ontology_files:
        files_list = [str(path) for path in ontology_files]
    else:
        warn(f"No ontology files found in {ontology_dir}")
        files_list = []

    create = subprocess.run(
        k8s_create_configmap(
            name="graphdb-ontologies",
            files=files_list,
            namespace="graphdb"
        ),
        capture_output=True,
        text=True,
        check=True,
    )
    _kubectl_apply_stdin(create.stdout, dry_run=dry_run)

    with console.status("[bold green]Applying GraphDB storage, config, deployment and service...[/bold green]"):
        for manifest in ("01-storage.yaml", "02-init-config.yaml", "03-deployment.yaml", "04-service.yaml"):
            run(k8s_apply_manifest(str(graphdb_dir / manifest), dry_run=dry_run))

    if not dry_run:
        with console.status("[bold green]Waiting for GraphDB deployment to become ready...[/bold green]"):
            run(k8s_rollout_status("deployment/graphdb", namespace="graphdb"))
            run(k8s_wait_ready("pod", label_selector="app=graphdb", namespace="graphdb"))
        info("GraphDB is ready.")
    else:
        info("[yellow](Dry Run) Skipped waiting for GraphDB deployment readiness.[/yellow]")

def _wait_for_rabbitmq(dry_run: bool = False) -> None:
    """Block until RabbitMQ is ready to accept AMQP connections."""
    if not dry_run:
        with console.status("[bold green]Waiting for RabbitMQ deployment to become ready...[/bold green]"):
            run(k8s_rollout_status("deployment/rabbitmq", namespace="rabbitmq"))
            run(k8s_wait_ready("pod", label_selector="app=rabbitmq", namespace="rabbitmq"))
        info("RabbitMQ is ready.")
    else:
        info("[yellow](Dry Run) Skipped waiting for RabbitMQ deployment readiness.[/yellow]")

@app.callback(invoke_without_command=True)
def deploy_cmd(
    ctx: typer.Context,
    app_name: Annotated[Optional[str], typer.Option("--app", help="App name to deploy")] = None,
    all_apps: Annotated[bool, typer.Option("--all", help="Deploy all")] = False,
    dry_run: Annotated[bool, typer.Option("--dry-run", help="Execute in dry-run mode (server dry-run, skip rollout checks)")] = False,
):
    """Deploy AMoCNA stack to Kubernetes."""
    if ctx.invoked_subcommand is not None:
        return

    cfg: ProjectConfig = ctx.obj

    if app_name:
        error(
            "Per-app deploy not yet implemented. Use --all or deploy specific manifests manually."
        )
        sys.exit(1)

    if dry_run:
        warn("DRY RUN MODE ACTIVE — server-side dry-run will occur, no actual resources will be created/changed")

    header("Deploying AMoCNA to Kubernetes")

    _deploy_graphdb(cfg, dry_run=dry_run)

    full_path = cfg.project_root / "infra"
    with console.status(f"[bold green]Applying Kubernetes Kustomization in {full_path}...[/bold green]"):
        run(k8s_apply_kustomization(str(full_path), dry_run=dry_run))

    _wait_for_rabbitmq(dry_run=dry_run)

    if dry_run:
        info("Dry Run completed successfully. (Commands shown but cluster state unchanged)")
    else:
        info("Deployment commands sent.")
        warn("It may take a few minutes for all pods to reach 'Running' state.")
