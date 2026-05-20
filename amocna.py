#!/usr/bin/env python3
"""
amocna.py — Unified orchestration CLI for the AMoCNA project.

Usage:
    ./amocna.py build   [--app NAME|--all] [--push] [--registry REG] [--tag TAG]
    ./amocna.py test    [--app NAME|--all]
    ./amocna.py deploy  [--app NAME|--all]
    ./amocna.py undeploy [--app NAME|--all]
    ./amocna.py forward <name> [--local-port PORT]
    ./amocna.py version [--bump major|minor|patch] [--set VER] [--dry-run]
    ./amocna.py status

Requires: Python 3.10+, no external dependencies.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import textwrap
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

# ─── YAML mini-parser (no PyYAML dependency) ──────────────────────────


def _parse_yaml(path: Path) -> dict:
    """Minimal YAML parser supporting scalars, maps, lists, and flow mappings.

    Good enough for amocna.yaml — NOT a full YAML implementation.
    """
    lines = path.read_text().splitlines()
    return _parse_block(lines, 0, 0)[0]


def _current_indent(line: str) -> int:
    return len(line) - len(line.lstrip())


def _parse_block(lines: list[str], idx: int, base_indent: int) -> tuple[dict, int]:
    result: dict = {}
    while idx < len(lines):
        line = lines[idx]
        stripped = line.strip()

        # skip blanks and comments
        if not stripped or stripped.startswith("#"):
            idx += 1
            continue

        indent = _current_indent(line)
        if indent < base_indent:
            break

        if indent > base_indent:
            # shouldn't happen at top level but skip gracefully
            idx += 1
            continue

        if ":" not in stripped:
            idx += 1
            continue

        # Handle "- { key: val, ... }" list items
        if stripped.startswith("- "):
            # This is a list item at this indent level — find the key that owns it
            # We've already moved past the key, so this is handled below
            break

        key_part, _, val_part = stripped.partition(":")
        key = key_part.strip()
        val_part = val_part.strip()

        if val_part:
            # inline value
            result[key] = _parse_inline(val_part)
            idx += 1
        else:
            # check next line
            next_idx = idx + 1
            # skip blanks/comments
            while next_idx < len(lines) and (
                not lines[next_idx].strip() or lines[next_idx].strip().startswith("#")
            ):
                next_idx += 1

            if next_idx >= len(lines):
                result[key] = None
                idx = next_idx
            else:
                next_indent = _current_indent(lines[next_idx])
                next_stripped = lines[next_idx].strip()
                if next_indent <= indent:
                    result[key] = None
                    idx = next_idx
                elif next_stripped.startswith("- "):
                    # list
                    lst, idx = _parse_list(lines, next_idx, next_indent)
                    result[key] = lst
                else:
                    # nested map
                    nested, idx = _parse_block(lines, next_idx, next_indent)
                    result[key] = nested

    return result, idx


def _parse_list(lines: list[str], idx: int, base_indent: int) -> tuple[list, int]:
    result = []
    while idx < len(lines):
        line = lines[idx]
        stripped = line.strip()

        if not stripped or stripped.startswith("#"):
            idx += 1
            continue

        indent = _current_indent(line)
        if indent < base_indent:
            break

        if not stripped.startswith("- "):
            break

        item_val = stripped[2:].strip()
        result.append(_parse_inline(item_val))
        idx += 1

    return result, idx


def _parse_inline(val: str) -> Any:
    """Parse an inline YAML value: flow mapping, list, or scalar."""
    val = val.strip()

    # flow mapping { key: val, ... }
    if val.startswith("{") and val.endswith("}"):
        inner = val[1:-1].strip()
        if not inner:
            return {}
        result = {}
        for pair in _split_flow(inner):
            pair = pair.strip()
            if ":" in pair:
                k, _, v = pair.partition(":")
                result[k.strip()] = _parse_scalar(v.strip())
        return result

    # flow list [ ... ]
    if val.startswith("[") and val.endswith("]"):
        inner = val[1:-1].strip()
        if not inner:
            return []
        return [_parse_scalar(x.strip()) for x in _split_flow(inner)]

    return _parse_scalar(val)


def _split_flow(s: str) -> list[str]:
    """Split a flow collection by commas, respecting nested braces."""
    parts = []
    depth = 0
    current: list[str] = []
    for ch in s:
        if ch in "{[":
            depth += 1
        elif ch in "}]":
            depth -= 1
        elif ch == "," and depth == 0:
            parts.append("".join(current))
            current = []
            continue
        current.append(ch)
    if current:
        parts.append("".join(current))
    return parts


def _parse_scalar(val: str) -> Any:
    if not val:
        return None
    # strip quotes
    if (val.startswith('"') and val.endswith('"')) or (
        val.startswith("'") and val.endswith("'")
    ):
        return val[1:-1]
    # strip inline comments
    if "  #" in val:
        val = val[: val.index("  #")].strip()
    elif val.endswith("#"):
        pass  # edge case, leave it
    # booleans
    if val.lower() in ("true", "yes"):
        return True
    if val.lower() in ("false", "no"):
        return False
    if val.lower() in ("null", "~"):
        return None
    # numbers
    try:
        return int(val)
    except ValueError:
        pass
    try:
        return float(val)
    except ValueError:
        pass
    return val


# ─── ANSI colors ──────────────────────────────────────────────────────


class _C:
    """ANSI color helpers."""

    _enabled = sys.stdout.isatty()

    @staticmethod
    def _w(code: str, text: str) -> str:
        return f"\033[{code}m{text}\033[0m" if _C._enabled else text

    @staticmethod
    def bold(t: str) -> str:
        return _C._w("1", t)

    @staticmethod
    def green(t: str) -> str:
        return _C._w("32", t)

    @staticmethod
    def yellow(t: str) -> str:
        return _C._w("33", t)

    @staticmethod
    def red(t: str) -> str:
        return _C._w("31", t)

    @staticmethod
    def cyan(t: str) -> str:
        return _C._w("36", t)

    @staticmethod
    def dim(t: str) -> str:
        return _C._w("2", t)


def info(msg: str) -> None:
    print(f"{_C.green('✔')} {msg}")


def warn(msg: str) -> None:
    print(f"{_C.yellow('⚠')} {msg}")


def error(msg: str) -> None:
    print(f"{_C.red('✖')} {msg}", file=sys.stderr)


def header(msg: str) -> None:
    width = 60
    print()
    print(_C.cyan("─" * width))
    print(_C.bold(f"  {msg}"))
    print(_C.cyan("─" * width))


# ─── Project model ────────────────────────────────────────────────────


@dataclass
class AppDef:
    name: str
    path: str
    app_type: str
    dockerfile: str | None = None
    image_name: str | None = None
    ports: dict = field(default_factory=dict)
    description: str = ""
    is_core: bool = False


@dataclass
class ForwardDef:
    name: str
    namespace: str
    service: str
    local_port: int
    remote_port: int


@dataclass
class ProjectConfig:
    name: str
    group_id: str
    parent_pom: str
    registry: str
    apps: dict[str, AppDef]
    forwards: dict[str, ForwardDef]
    k8s_deploy_order: list[str]
    k8s_undeploy_namespaces: list[str]
    k8s_cluster_resources: list[dict]
    project_root: Path


# ─── Config loader ────────────────────────────────────────────────────


def find_project_root() -> Path:
    """Walk up from CWD or script dir to find amocna.yaml."""
    candidates = [Path.cwd(), Path(__file__).resolve().parent]
    for start in candidates:
        p = start
        while p != p.parent:
            if (p / "amocna.yaml").is_file():
                return p
            p = p.parent
    error("Cannot find amocna.yaml — run from the project root or its subdirectory.")
    sys.exit(1)


def load_config(root: Path) -> ProjectConfig:
    cfg = _parse_yaml(root / "amocna.yaml")

    project = cfg.get("project", {})
    apps: dict[str, AppDef] = {}

    for name, adef in (cfg.get("core_apps") or {}).items():
        if not isinstance(adef, dict):
            continue
        apps[name] = AppDef(
            name=name,
            path=adef.get("path", ""),
            app_type=adef.get("type", "maven-app"),
            dockerfile=adef.get("dockerfile"),
            image_name=adef.get("image_name", name),
            ports=adef.get("ports") or {},
            description=adef.get("description", ""),
            is_core=True,
        )

    for name, adef in (cfg.get("standalone_apps") or {}).items():
        if not isinstance(adef, dict):
            continue
        apps[name] = AppDef(
            name=name,
            path=adef.get("path", ""),
            app_type=adef.get("type", "maven-app"),
            dockerfile=adef.get("dockerfile"),
            image_name=adef.get("image_name", name),
            ports=adef.get("ports") or {},
            description=adef.get("description", ""),
            is_core=False,
        )

    forwards: dict[str, ForwardDef] = {}
    for name, fdef in (cfg.get("forward") or {}).items():
        if not isinstance(fdef, dict):
            continue
        forwards[name] = ForwardDef(
            name=name,
            namespace=fdef.get("namespace", "default"),
            service=fdef.get("service", name),
            local_port=int(fdef.get("local_port", 8080)),
            remote_port=int(fdef.get("remote_port", 8080)),
        )

    k8s = cfg.get("k8s") or {}

    return ProjectConfig(
        name=project.get("name", "amocna"),
        group_id=project.get("group_id", "com.kubiki"),
        parent_pom=project.get("parent_pom", "pom.xml"),
        registry=project.get("registry", "sglomski"),
        apps=apps,
        forwards=forwards,
        k8s_deploy_order=k8s.get("deploy_order") or [],
        k8s_undeploy_namespaces=k8s.get("undeploy_namespaces") or [],
        k8s_cluster_resources=k8s.get("cluster_resources") or [],
        project_root=root,
    )


def resolve_registry(args: argparse.Namespace, cfg: ProjectConfig) -> str:
    """Registry resolution: --registry flag > AMOCNA_REGISTRY env > amocna.yaml."""
    if hasattr(args, "registry") and args.registry:
        return args.registry
    env = os.environ.get("AMOCNA_REGISTRY")
    if env:
        return env
    return cfg.registry


# ─── Shell helpers ────────────────────────────────────────────────────


def run(
    cmd: list[str], cwd: Path | None = None, check: bool = True, capture: bool = False
) -> subprocess.CompletedProcess:
    """Run a subprocess, streaming output unless capture=True."""
    print(f"  {_C.dim('$')} {_C.dim(' '.join(cmd))}")
    return subprocess.run(
        cmd,
        cwd=cwd,
        check=check,
        capture_output=capture,
        text=True,
    )


# ─── POM version helpers ─────────────────────────────────────────────

_NS = "http://maven.apache.org/POM/4.0.0"
_VERSION_RE = re.compile(r"(<version>)(.*?)(</version>)")
_PARENT_BLOCK_RE = re.compile(r"(<parent>.*?</parent>)", re.DOTALL)


def read_pom_version(pom_path: Path) -> str:
    """Read the <version> directly under <project> (not inside <parent>)."""
    content = pom_path.read_text()
    # Remove parent block temporarily to avoid matching parent's version
    without_parent = _PARENT_BLOCK_RE.sub("", content)
    m = _VERSION_RE.search(without_parent)
    if m:
        return m.group(2)
    error(f"Could not find <version> in {pom_path}")
    sys.exit(1)


def _set_version_in_text(text: str, new_version: str) -> str:
    """Replace the first <version>...</version> NOT inside <parent> block."""
    # Strategy: split on <parent>...</parent>, replace first version outside it
    parts = _PARENT_BLOCK_RE.split(text)
    found = False
    result_parts = []
    for part in parts:
        if _PARENT_BLOCK_RE.fullmatch(part):
            # this is a parent block, leave as-is
            result_parts.append(part)
        elif not found:
            new_part, count = _VERSION_RE.subn(
                rf"\g<1>{new_version}\g<3>", part, count=1
            )
            if count > 0:
                found = True
            result_parts.append(new_part)
        else:
            result_parts.append(part)
    return "".join(result_parts)


def _set_parent_version_in_text(text: str, new_version: str) -> str:
    """Replace <version> inside <parent>...</parent> block."""

    def _replace_in_parent(m: re.Match) -> str:
        parent_block = m.group(1)
        return _VERSION_RE.sub(rf"\g<1>{new_version}\g<3>", parent_block, count=1)

    return _PARENT_BLOCK_RE.sub(_replace_in_parent, text)


def bump_version(current: str, part: str) -> str:
    """Bump a Maven version string like 1.0-SNAPSHOT or 1.2.3-SNAPSHOT."""
    # Strip -SNAPSHOT suffix
    suffix = ""
    base = current
    if "-" in current:
        base, suffix = current.split("-", 1)
        suffix = f"-{suffix}"

    segments = base.split(".")
    # Ensure at least 3 segments
    while len(segments) < 3:
        segments.append("0")

    if part == "major":
        segments[0] = str(int(segments[0]) + 1)
        segments[1] = "0"
        segments[2] = "0"
    elif part == "minor":
        segments[1] = str(int(segments[1]) + 1)
        segments[2] = "0"
    elif part == "patch":
        segments[2] = str(int(segments[2]) + 1)
    else:
        error(f"Unknown bump part: {part}")
        sys.exit(1)

    return ".".join(segments) + suffix


# ─── Subcommands ──────────────────────────────────────────────────────


def cmd_status(cfg: ProjectConfig, _args: argparse.Namespace) -> None:
    """Show project overview and app status."""
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


def cmd_build(cfg: ProjectConfig, args: argparse.Namespace) -> None:
    """Build Docker images for specified apps."""
    registry = resolve_registry(args, cfg)
    tag = args.tag or "latest"
    apps_to_build = _resolve_apps(cfg, args)

    for app in apps_to_build:
        if not app.dockerfile:
            if app.app_type == "maven-lib":
                info(f"Skipping {app.name} (library, no Docker image)")
            else:
                warn(f"Skipping {app.name} (no dockerfile configured)")
            continue

        header(f"Building {app.name}")
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

        if args.push:
            info(f"Pushing {image}...")
            run(["docker", "push", image])

        info(f"{app.name} built successfully")


def cmd_test(cfg: ProjectConfig, args: argparse.Namespace) -> None:
    """Run Maven tests for specified apps."""
    apps_to_test = _resolve_apps(cfg, args)

    for app in apps_to_test:
        if app.app_type not in ("maven-app", "maven-lib"):
            warn(f"Skipping {app.name} (not a Maven project)")
            continue

        header(f"Testing {app.name}")
        pom_path = cfg.project_root / app.path / "pom.xml"
        if not pom_path.is_file():
            error(f"pom.xml not found: {pom_path}")
            sys.exit(1)

        run(["mvn", "test", "-f", str(pom_path)])
        info(f"{app.name} tests passed")


def cmd_deploy(cfg: ProjectConfig, args: argparse.Namespace) -> None:
    """Deploy to Kubernetes using manifests."""
    if args.app:
        error(
            "Per-app deploy not yet implemented. Use --all or deploy specific manifests manually."
        )
        sys.exit(1)

    header("Deploying AMoCNA to Kubernetes")

    full_path = cfg.project_root / "infra"
    info(f"Applying kustomization in {full_path}")
    run(["kubectl", "apply", "-k", str(full_path)])

    info("Deployment commands sent.")
    warn("It may take a few minutes for all pods to reach 'Running' state.")


def cmd_undeploy(cfg: ProjectConfig, _args: argparse.Namespace) -> None:
    """Remove AMoCNA from the Kubernetes cluster."""
    header("Undeploying AMoCNA from Kubernetes")

    full_path = cfg.project_root / "infra"
    info(f"Deleting kustomization in {full_path}")
    run(["kubectl", "delete", "-k", str(full_path), "--ignore-not-found"], check=False)

    info("AMoCNA has been removed from the cluster.")


def cmd_forward(cfg: ProjectConfig, args: argparse.Namespace) -> None:
    """Port-forward a service from Kubernetes."""
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


def cmd_version(cfg: ProjectConfig, args: argparse.Namespace) -> None:
    """Synchronize versions across parent POM and all core app POMs."""
    parent_pom = cfg.project_root / cfg.parent_pom
    current = read_pom_version(parent_pom)

    header("Version Synchronization")
    info(f"Current version: {_C.bold(current)}")

    # Determine new version
    if args.set:
        new_version = args.set
    elif args.bump:
        new_version = bump_version(current, args.bump)
    else:
        # Just show current state
        print(f"\n  {_C.bold('Parent POM')}: {current}")
        core_apps = [a for a in cfg.apps.values() if a.is_core]
        for app in core_apps:
            pom = cfg.project_root / app.path / "pom.xml"
            if pom.is_file():
                v = read_pom_version(pom)
                match = (
                    _C.green("✔ in sync")
                    if v == current
                    else _C.red(f"✖ out of sync ({v})")
                )
                print(f"  {app.name:<20}: {match}")
        print()
        return

    info(f"New version: {_C.bold(new_version)}")

    # Collect all POM files to update
    poms_to_update: list[tuple[str, Path]] = [("parent", parent_pom)]
    core_apps = [a for a in cfg.apps.values() if a.is_core]
    for app in core_apps:
        pom = cfg.project_root / app.path / "pom.xml"
        if pom.is_file():
            poms_to_update.append((app.name, pom))

    if args.dry_run:
        warn("DRY RUN — no files will be modified")
        print()
        for label, pom in poms_to_update:
            print(f"  Would update: {_C.bold(label):<25} {_C.dim(str(pom))}")
        print()
        return

    # Apply changes
    for label, pom in poms_to_update:
        text = pom.read_text()

        if label == "parent":
            # Update <version> under <project>
            new_text = _set_version_in_text(text, new_version)
        else:
            # Update <parent><version> and module's own <version>
            new_text = _set_parent_version_in_text(text, new_version)
            new_text = _set_version_in_text(new_text, new_version)

        if new_text != text:
            pom.write_text(new_text)
            info(f"Updated {label}: {pom.name}")
        else:
            warn(f"No change needed for {label}")

    print()
    info(f"All core POMs synchronized to {_C.bold(new_version)}")
    print(
        f"  {_C.dim('Tip: run')} ./amocna.py version {_C.dim('to verify sync state')}"
    )
    print()


# ─── Helpers ──────────────────────────────────────────────────────────


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


# ─── CLI setup ────────────────────────────────────────────────────────


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="amocna.py",
        description="Unified orchestration CLI for the AMoCNA project.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=textwrap.dedent("""\
            examples:
              ./amocna.py status
              ./amocna.py build --app themis --push
              ./amocna.py build --all --registry ghcr.io/amocna
              ./amocna.py test --app metis
              ./amocna.py deploy --all
              ./amocna.py forward gui-backend
              ./amocna.py version --bump minor --dry-run
              ./amocna.py version --set 2.0.0-SNAPSHOT
        """),
    )
    sub = parser.add_subparsers(dest="command", required=True)

    # status
    sub.add_parser("status", help="Show project overview and app status")

    # build
    p_build = sub.add_parser("build", help="Build Docker images")
    p_build.add_argument("--app", help="App name to build")
    p_build.add_argument("--all", action="store_true", help="Build all apps")
    p_build.add_argument("--push", action="store_true", help="Push image after build")
    p_build.add_argument("--registry", help="Docker registry (overrides config & env)")
    p_build.add_argument("--tag", default="latest", help="Image tag (default: latest)")

    # test
    p_test = sub.add_parser("test", help="Run Maven tests")
    p_test.add_argument("--app", help="App name to test")
    p_test.add_argument("--all", action="store_true", help="Test all apps")

    # deploy
    p_deploy = sub.add_parser("deploy", help="Deploy to Kubernetes")
    p_deploy.add_argument("--app", help="App name to deploy")
    p_deploy.add_argument("--all", action="store_true", help="Deploy all")

    # undeploy
    sub.add_parser("undeploy", help="Remove AMoCNA from Kubernetes")

    # forward
    p_fwd = sub.add_parser("forward", help="Port-forward a K8s service")
    p_fwd.add_argument("name", help="Forward target name")
    p_fwd.add_argument("--local-port", type=int, help="Override local port")

    # version
    p_ver = sub.add_parser("version", help="Manage & sync versions across core POMs")
    ver_group = p_ver.add_mutually_exclusive_group()
    ver_group.add_argument(
        "--bump", choices=["major", "minor", "patch"], help="Bump version component"
    )
    ver_group.add_argument("--set", help="Set an explicit version string")
    p_ver.add_argument(
        "--dry-run", action="store_true", help="Show what would change without writing"
    )

    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()

    root = find_project_root()
    cfg = load_config(root)

    commands = {
        "status": cmd_status,
        "build": cmd_build,
        "test": cmd_test,
        "deploy": cmd_deploy,
        "undeploy": cmd_undeploy,
        "forward": cmd_forward,
        "version": cmd_version,
    }

    handler = commands.get(args.command)
    if handler:
        try:
            handler(cfg, args)
        except subprocess.CalledProcessError as e:
            error(f"Command failed with exit code {e.returncode}")
            sys.exit(e.returncode)
        except KeyboardInterrupt:
            print()
            sys.exit(130)
    else:
        parser.print_help()
        sys.exit(1)


if __name__ == "__main__":
    main()
