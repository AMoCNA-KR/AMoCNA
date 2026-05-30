import argparse
from cli.plugins.base import BasePlugin
from cli.core import ProjectConfig, header, _C
from cli.plugins.version import read_pom_version

class StatusPlugin(BasePlugin):
    """Plugin to show project overview and app status."""

    def register(self, subparsers: argparse._SubParsersAction) -> None:
        p_status = subparsers.add_parser("status", help="Show project overview and app status")
        p_status.set_defaults(handler=self.execute)

    def execute(self, cfg: ProjectConfig, args: argparse.Namespace) -> None:
        header("AMoCNA Project Status")

        current_version = read_pom_version(cfg.project_root / cfg.parent_pom)
        print(f"\n  Project:  {_C.bold(cfg.name)}")
        print(f"  Root:     {cfg.project_root}")
        print(f"  Registry: {cfg.registry}")
        print(f"  Version:  {_C.bold(current_version)} {_C.dim('(core apps)')}")

        print(f"\n  {_C.bold('Core Apps')} {_C.dim('(version-synced)')}")
        for name, app in cfg.apps.items():
            if not app.is_core:
                continue
            exists = (cfg.project_root / app.path).is_dir()
            marker = _C.green("●") if exists else _C.red("●")
            dtype = _C.dim(f"[{app.app_type}]")
            print(f"    {marker} {name:<20} {dtype}  {app.description}")

        print(f"\n  {_C.bold('Standalone Apps')} {_C.dim('(independent versioning)')}")
        for name, app in cfg.apps.items():
            if app.is_core:
                continue
            exists = (cfg.project_root / app.path).is_dir()
            marker = _C.green("●") if exists else _C.red("●")
            dtype = _C.dim(f"[{app.app_type}]")
            print(f"    {marker} {name:<20} {dtype}  {app.description}")

        print(f"\n  {_C.bold('Forward Shortcuts')}")
        for name, fwd in cfg.forwards.items():
            print(
                f"    {name:<20} → localhost:{fwd.local_port} → {fwd.namespace}/{fwd.service}:{fwd.remote_port}"
            )
        print()
