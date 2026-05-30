import argparse
import os
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
from cli.plugins.version import read_pom_version
from cli.plugins.login import check_pat

def resolve_registry(args: argparse.Namespace, cfg: ProjectConfig) -> str:
    """Registry resolution: --registry flag > AMOCNA_REGISTRY env > amocna.yaml."""
    if hasattr(args, "registry") and args.registry:
        return args.registry
    env = os.environ.get("AMOCNA_REGISTRY")
    if env:
        return env
    return cfg.registry

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

class BuildPlugin(BasePlugin):
    """Plugin to build and push Docker images."""

    def register(self, subparsers: argparse._SubParsersAction) -> None:
        p_build = subparsers.add_parser("build", help="Build Docker images")
        p_build.add_argument("--app", help="App name to build")
        p_build.add_argument("--all", action="store_true", help="Build all apps")
        p_build.add_argument("--push", action="store_true", help="Push image after build")
        p_build.add_argument("--registry", help="Docker registry (overrides config & env)")
        p_build.add_argument("--tag", help="Image tag (default: version from pom.xml)")
        p_build.set_defaults(handler=self.execute)

    def execute(self, cfg: ProjectConfig, args: argparse.Namespace) -> None:
        registry = resolve_registry(args, cfg)
        tag = args.tag
        if not tag:
            tag = read_pom_version(cfg.project_root / cfg.parent_pom)
        apps_to_build = _resolve_apps(cfg, args)

        if args.push and "ghcr.io" in registry:
            check_pat()

        for app in apps_to_build:
            if not app.dockerfile:
                if app.app_type in ("maven-lib", "angular"):
                    info(f"Skipping {app.name} ({app.app_type}, no Docker image)")
                else:
                    warn(f"Skipping {app.name} (no dockerfile configured)")
                continue

            header(f"Building {app.name}")
            self._docker_build_image(cfg, app, registry, tag, push=args.push)
            info(f"{app.name} built successfully")

    def _docker_build_image(
        self,
        cfg: ProjectConfig,
        app: AppDef,
        registry: str,
        tag: str,
        *,
        push: bool = False,
    ) -> str:
        """Build (and optionally push) a Docker image for an app. Returns the image ref."""
        if not app.dockerfile:
            error(f"No dockerfile configured for {app.name}")
            sys.exit(1)

        dockerfile_path = cfg.project_root / app.dockerfile
        if not dockerfile_path.is_file():
            error(f"Dockerfile not found: {dockerfile_path}")
            sys.exit(1)

        image = f"{registry}/{app.image_name}:{tag}"
        info(f"Image: {image}")
        info(f"Dockerfile: {app.dockerfile}")

        run(
            [
                "docker",
                "build",
                "-t",
                image,
                "-f",
                str(dockerfile_path),
                str(cfg.project_root),
            ]
        )

        if push:
            info(f"Pushing {image}...")
            run(["docker", "push", image])

        return image
