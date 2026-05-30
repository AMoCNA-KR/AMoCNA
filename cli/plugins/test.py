import argparse
import sys
from cli.plugins.base import BasePlugin
from cli.core import (
    ProjectConfig,
    AppDef,
    header,
    info,
    warn,
    error,
    run,
)

def _resolve_apps(cfg: ProjectConfig, args: argparse.Namespace) -> list[AppDef]:
    """Resolve which apps to operate on from --app / --all flags."""
    if getattr(args, "all", False):
        return list(cfg.apps.values())

    app_name = getattr(args, "app", None)
    if not app_name:
        error("Specify --app NAME or --all")
        error(f"Available apps: {', '.join(cfg.apps.keys())}")
        sys.exit(1)

    if app_name not in cfg.apps:
        error(f"Unknown app: {app_name}")
        error(f"Available: {', '.join(cfg.apps.keys())}")
        sys.exit(1)

    return [cfg.apps[app_name]]

class TestPlugin(BasePlugin):
    """Plugin to run Maven tests."""

    def register(self, subparsers: argparse._SubParsersAction) -> None:
        p_test = subparsers.add_parser("test", help="Run Maven tests")
        p_test.add_argument("--app", help="App name to test")
        p_test.add_argument("--all", action="store_true", help="Test all apps")
        p_test.set_defaults(handler=self.execute)

    def execute(self, cfg: ProjectConfig, args: argparse.Namespace) -> None:
        apps_to_test = _resolve_apps(cfg, args)
        parent_pom = cfg.project_root / cfg.parent_pom

        for app in apps_to_test:
            if app.app_type not in ("maven-app", "maven-lib"):
                warn(f"Skipping {app.name} (not a Maven project)")
                continue

            header(f"Testing {app.name}")
            pom_path = cfg.project_root / app.path / "pom.xml"
            if not pom_path.is_file():
                error(f"pom.xml not found: {pom_path}")
                sys.exit(1)

            if app.is_core:
                if not parent_pom.is_file():
                    error(f"parent POM not found: {parent_pom}")
                    sys.exit(1)
                run(
                    ["mvn", "test", "-pl", app.path, "-am"],
                    cwd=cfg.project_root,
                )
            else:
                run(["mvn", "test", "-f", str(pom_path)])
            info(f"{app.name} tests passed")
