import argparse
import sys
from cli.plugins.base import BasePlugin
from cli.core import (
    ProjectConfig,
    header,
    info,
    error,
    run,
    _C,
)

class ForwardPlugin(BasePlugin):
    """Plugin to port-forward a service from Kubernetes."""

    def register(self, subparsers: argparse._SubParsersAction) -> None:
        p_fwd = subparsers.add_parser("forward", help="Port-forward a K8s service")
        p_fwd.add_argument("name", help="Forward target name")
        p_fwd.add_argument("--local-port", type=int, help="Override local port")
        p_fwd.set_defaults(handler=self.execute)

    def execute(self, cfg: ProjectConfig, args: argparse.Namespace) -> None:
        name = args.name
        if name not in cfg.forwards:
            available = ", ".join(cfg.forwards.keys())
            error(f"Unknown forward target: {name}")
            error(f"Available: {available}")
            sys.exit(1)

        fwd = cfg.forwards[name]
        local_port = args.local_port or fwd.local_port

        header(f"Forwarding {name}")
        info(
            f"http://localhost:{local_port} → {fwd.namespace}/{fwd.service}:{fwd.remote_port}"
        )
        print(f"  {_C.dim('Press Ctrl+C to stop.')}\n")

        try:
            run(
                [
                    "kubectl",
                    "port-forward",
                    "-n",
                    fwd.namespace,
                    f"svc/{fwd.service}",
                    f"{local_port}:{fwd.remote_port}",
                ]
            )
        except KeyboardInterrupt:
            print()
            info("Forwarding stopped.")
