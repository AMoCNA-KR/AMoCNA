import argparse
import subprocess
import textwrap
from cli.plugins.base import BasePlugin
from cli.core import (
    ProjectConfig,
    header,
    info,
    warn,
    run,
)

class UndeployPlugin(BasePlugin):
    """Plugin to remove AMoCNA from the Kubernetes cluster."""

    def register(self, subparsers: argparse._SubParsersAction) -> None:
        p_undeploy = subparsers.add_parser("undeploy", help="Remove AMoCNA from Kubernetes")
        p_undeploy.add_argument(
            "--keep-graphdb-data",
            action="store_true",
            help="Do not wipe GraphDB hostPath data (old triples may remain after redeploy)",
        )
        p_undeploy.set_defaults(handler=self.execute)

    def execute(self, cfg: ProjectConfig, args: argparse.Namespace) -> None:
        header("Undeploying AMoCNA from Kubernetes")

        full_path = cfg.project_root / "infra"
        info(f"Deleting kustomization in {full_path}")
        run(["kubectl", "delete", "-k", str(full_path), "--ignore-not-found"], check=False)

        self._undeploy_graphdb(cfg, keep_data=args.keep_graphdb_data)

        info("AMoCNA has been removed from the cluster.")

    def _kubectl_apply_stdin(self, manifest_yaml: str) -> None:
        subprocess.run(
            ["kubectl", "apply", "-f", "-"],
            input=manifest_yaml,
            text=True,
            check=True,
        )

    def _wipe_graphdb_host_data(self, cfg: ProjectConfig) -> None:
        """Delete GraphDB files on the PV hostPath (survives namespace/PV deletion)."""
        host_path = cfg.graphdb_host_path
        node = cfg.graphdb_node_hostname
        job_name = "amocna-graphdb-wipe"

        header("Wiping GraphDB persistent data")
        info(f"Host path {host_path} on node {node}")

        manifest = textwrap.dedent(
            f"""\
            apiVersion: batch/v1
            kind: Job
            metadata:
              name: {job_name}
              namespace: default
            spec:
              ttlSecondsAfterFinished: 120
              backoffLimit: 0
              template:
                spec:
                  restartPolicy: Never
                  nodeSelector:
                    kubernetes.io/hostname: {node}
                  containers:
                    - name: wipe
                      image: busybox:1.36
                      command:
                        - sh
                        - -c
                        - |
                          set -e
                          echo "Wiping GraphDB data under /mnt/graphdb ..."
                          rm -rf /mnt/graphdb/*
                          rm -rf /mnt/graphdb/.[!.]* /mnt/graphdb/..?* 2>/dev/null || true
                          echo "Done."
                      volumeMounts:
                        - name: graphdb-data
                          mountPath: /mnt/graphdb
                  volumes:
                    - name: graphdb-data
                      hostPath:
                        path: {host_path}
                        type: DirectoryOrCreate
            """
        )

        run(
            ["kubectl", "delete", "job", job_name, "-n", "default", "--ignore-not-found"],
            check=False,
        )
        self._kubectl_apply_stdin(manifest)

        result = run(
            [
                "kubectl",
                "wait",
                "--for=condition=complete",
                f"job/{job_name}",
                "-n",
                "default",
                "--timeout=120s",
            ],
            check=False,
        )
        run(
            ["kubectl", "delete", "job", job_name, "-n", "default", "--ignore-not-found"],
            check=False,
        )

        if result.returncode != 0:
            warn(
                f"Could not wipe GraphDB data at {host_path}. "
                f"Remove it manually on node {node} if stale triples remain."
            )
        else:
            info("GraphDB host data wiped.")

    def _undeploy_graphdb(self, cfg: ProjectConfig, *, keep_data: bool = False) -> None:
        """Remove GraphDB and optionally wipe its hostPath volume."""
        if not keep_data:
            info("Stopping GraphDB before wiping data...")
            run(
                [
                    "kubectl",
                    "delete",
                    "deployment",
                    "graphdb",
                    "-n",
                    "graphdb",
                    "--ignore-not-found",
                    "--wait=true",
                    "--timeout=120s",
                ],
                check=False,
            )
            self._wipe_graphdb_host_data(cfg)
        else:
            warn("Keeping GraphDB hostPath data (--keep-graphdb-data). Old repository data will remain.")

        run(["kubectl", "delete", "namespace", "graphdb", "--ignore-not-found", "--timeout=60s"], check=False)
        run(["kubectl", "delete", "pv", "graphdb-pv", "--ignore-not-found"], check=False)
        run(["kubectl", "delete", "storageclass", "local-storage", "--ignore-not-found"], check=False)
