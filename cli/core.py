from __future__ import annotations

import argparse
import importlib
import pkgutil
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import cli.plugins
from cli.plugins.base import BasePlugin


def _parse_yaml(path: Path) -> dict:
    """Minimal YAML parser supporting scalars, maps, lists, and flow mappings."""
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
    graphdb_host_path: str
    graphdb_node_hostname: str
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
    graphdb_storage = k8s.get("graphdb_storage") or {}

    return ProjectConfig(
        name=project.get("name", "amocna"),
        group_id=project.get("group_id", "com.kubiki"),
        parent_pom=project.get("parent_pom", "pom.xml"),
        registry=project.get("registry", "ghcr.io/amocna-kr"),
        apps=apps,
        forwards=forwards,
        k8s_deploy_order=k8s.get("deploy_order") or [],
        k8s_undeploy_namespaces=k8s.get("undeploy_namespaces") or [],
        k8s_cluster_resources=k8s.get("cluster_resources") or [],
        graphdb_host_path=graphdb_storage.get("host_path", "/data/graphdb"),
        graphdb_node_hostname=graphdb_storage.get("node_hostname", "kube-worker-0"),
        project_root=root,
    )


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


def run_capture(cmd: list[str], cwd: Path | None = None, check: bool = True) -> str:
    """Run a subprocess and capture stdout."""
    res = subprocess.run(cmd, cwd=cwd, check=check, capture_output=True, text=True)
    return res.stdout.strip()


# ─── Dynamic plugin discovery ─────────────────────────────────────────


def discover_plugins() -> list[BasePlugin]:
    """Dynamically discover and instantiate all plugin classes under cli.plugins."""
    plugins: list[BasePlugin] = []
    package = cli.plugins
    for _, module_name, is_pkg in pkgutil.iter_modules(package.__path__):
        if is_pkg:
            continue
        if module_name == "base":
            continue

        full_module_name = f"cli.plugins.{module_name}"
        module = importlib.import_module(full_module_name)

        # Look for subclasses of BasePlugin defined in this module
        for attr_name in dir(module):
            attr = getattr(module, attr_name)
            if (
                isinstance(attr, type)
                and issubclass(attr, BasePlugin)
                and attr is not BasePlugin
            ):
                plugins.append(attr())

    return plugins


def main() -> None:
    parser = argparse.ArgumentParser(
        prog="amocna.py",
        description="Unified orchestration CLI for the AMoCNA project.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )

    subparsers = parser.add_subparsers(dest="command", required=True)

    # Load and register all plugins dynamically
    plugins = discover_plugins()
    for plugin in plugins:
        plugin.register(subparsers)

    args = parser.parse_args()

    root = find_project_root()
    cfg = load_config(root)

    if hasattr(args, "handler"):
        try:
            args.handler(cfg, args)
        except subprocess.CalledProcessError as e:
            error(f"Command failed with exit code {e.returncode}")
            sys.exit(e.returncode)
        except KeyboardInterrupt:
            print()
            sys.exit(130)
    else:
        parser.print_help()
        sys.exit(1)
