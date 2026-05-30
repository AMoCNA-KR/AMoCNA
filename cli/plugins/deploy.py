import argparse
import base64
import subprocess
import sys
from cli.plugins.base import BasePlugin
from cli.core import (
    ProjectConfig,
    header,
    info,
    warn,
    error,
    run,
)

class DeployPlugin(BasePlugin):
    """Plugin to deploy AMoCNA to Kubernetes using manifests."""

    def register(self, subparsers: argparse._SubParsersAction) -> None:
        p_deploy = subparsers.add_parser("deploy", help="Deploy to Kubernetes")
        p_deploy.add_argument("--app", help="App name to deploy")
        p_deploy.add_argument("--all", action="store_true", help="Deploy all")
        p_deploy.set_defaults(handler=self.execute)

    def execute(self, cfg: ProjectConfig, args: argparse.Namespace) -> None:
        if args.app:
            error(
                "Per-app deploy not yet implemented. Use --all or deploy specific manifests manually."
            )
            sys.exit(1)

        header("Deploying AMoCNA to Kubernetes")

        self._deploy_graphdb(cfg)

        full_path = cfg.project_root / "infra"
        info(f"Applying kustomization in {full_path}")
        run(["kubectl", "apply", "-k", str(full_path)])

        self._wait_for_rabbitmq()

        info("Deployment commands sent.")
        warn("It may take a few minutes for all pods to reach 'Running' state.")

    def _kubectl_apply_stdin(self, manifest_yaml: str) -> None:
        subprocess.run(
            ["kubectl", "apply", "-f", "-"],
            input=manifest_yaml,
            text=True,
            check=True,
        )

    def _deploy_graphdb(self, cfg: ProjectConfig) -> None:
        """Deploy GraphDB"""
        graphdb_dir = cfg.project_root / "infra" / "graphdb"
        ontology_dir = cfg.project_root / "libs" / "ontology"

        header("Deploying GraphDB")
        run(["kubectl", "apply", "-f", str(graphdb_dir / "00-namespace.yaml")])

        license_file = graphdb_dir / "graphdb.license"
        license_bin = graphdb_dir / "graphdb.license.bin"
        if license_file.is_file():
            info("Creating graphdb-license secret...")
            license_bin.write_bytes(base64.b64decode(license_file.read_bytes()))
            try:
                create = subprocess.run(
                    [
                        "kubectl",
                        "create",
                        "secret",
                        "generic",
                        "graphdb-license",
                        f"--from-file=GRAPHDB_LICENSE={license_bin}",
                        "--namespace=graphdb",
                        "--dry-run=client",
                        "-o",
                        "yaml",
                    ],
                    capture_output=True,
                    text=True,
                    check=True,
                )
                self._kubectl_apply_stdin(create.stdout)
            finally:
                license_bin.unlink(missing_ok=True)
        else:
            warn(f"No license found. Place graphdb.license in {graphdb_dir}")

        info("Creating graphdb-ontologies ConfigMap...")
        ontology_files = sorted(ontology_dir.glob("*.rdf"))
        cm_cmd = [
            "kubectl",
            "create",
            "configmap",
            "graphdb-ontologies",
            "--namespace=graphdb",
        ]
        if ontology_files:
            for path in ontology_files:
                cm_cmd.append(f"--from-file={path}")
        else:
            warn(f"No ontology files found in {ontology_dir}")
        create = subprocess.run(
            cm_cmd + ["--dry-run=client", "-o", "yaml"],
            capture_output=True,
            text=True,
            check=True,
        )
        self._kubectl_apply_stdin(create.stdout)

        for manifest in ("01-storage.yaml", "02-init-config.yaml", "03-deployment.yaml", "04-service.yaml"):
            run(["kubectl", "apply", "-f", str(graphdb_dir / manifest)])

        info("Waiting for GraphDB deployment to become ready...")
        run(
            [
                "kubectl",
                "rollout",
                "status",
                "deployment/graphdb",
                "-n",
                "graphdb",
                "--timeout=5m",
            ]
        )
        run(
            [
                "kubectl",
                "wait",
                "--for=condition=ready",
                "pod",
                "-l",
                "app=graphdb",
                "-n",
                "graphdb",
                "--timeout=5m",
            ]
        )
        info("GraphDB is ready.")

    def _wait_for_rabbitmq(self) -> None:
        """Block until RabbitMQ is ready to accept AMQP connections."""
        info("Waiting for RabbitMQ deployment to become ready...")
        run(
            [
                "kubectl",
                "rollout",
                "status",
                "deployment/rabbitmq",
                "-n",
                "rabbitmq",
                "--timeout=5m",
            ]
        )
        run(
            [
                "kubectl",
                "wait",
                "--for=condition=ready",
                "pod",
                "-l",
                "app=rabbitmq",
                "-n",
                "rabbitmq",
                "--timeout=5m",
            ]
        )
        info("RabbitMQ is ready.")
