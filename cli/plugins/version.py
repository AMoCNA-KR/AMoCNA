import argparse
import re
import sys
from pathlib import Path
from cli.plugins.base import BasePlugin
from cli.core import (
    ProjectConfig,
    header,
    info,
    warn,
    error,
    _C,
)

# ─── POM version helpers ─────────────────────────────────────────────

_VERSION_RE = re.compile(r"(<version>)(.*?)(</version>)")
_PARENT_BLOCK_RE = re.compile(r"(<parent>.*?</parent>)", re.DOTALL)
_EXCLUDE_BLOCKS_RE = re.compile(
    r"("
    r"<parent\b[^>]*>.*?</parent>|"
    r"<dependencies\b[^>]*>.*?</dependencies>|"
    r"<dependencyManagement\b[^>]*>.*?</dependencyManagement>|"
    r"<build\b[^>]*>.*?</build>|"
    r"<profiles\b[^>]*>.*?</profiles>|"
    r"<profile\b[^>]*>.*?</profile>|"
    r"<plugins\b[^>]*>.*?</plugins>|"
    r"<plugin\b[^>]*>.*?</plugin>"
    r")",
    re.DOTALL
)


def read_pom_version(pom_path: Path) -> str:
    """Read the <version> directly under <project> (not inside excluded blocks like parent, dependencies, build, etc.), falling back to parent version."""
    content = pom_path.read_text()
    clean_content = _EXCLUDE_BLOCKS_RE.sub("", content)
    
    m = _VERSION_RE.search(clean_content)
    if m:
        return m.group(2)
    m_parent = _PARENT_BLOCK_RE.search(content)
    if m_parent:
        m_ver = _VERSION_RE.search(m_parent.group(1))
        if m_ver:
            return m_ver.group(2)
    error(f"Could not find <version> in {pom_path}")
    sys.exit(1)


def _set_version_in_text(text: str, new_version: str) -> str:
    """Replace the first <version>...</version> NOT inside excluded blocks (parent, dependencies, build, profiles, etc.)."""
    parts = _EXCLUDE_BLOCKS_RE.split(text)
    found = False
    result_parts = []
    for i, part in enumerate(parts):
        if i % 2 == 1:
            result_parts.append(part)
        else:
            if not found:
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
    suffix = ""
    base = current
    if "-" in current:
        base, suffix = current.split("-", 1)
        suffix = f"-{suffix}"

    segments = base.split(".")
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


class VersionPlugin(BasePlugin):
    """Plugin to manage and synchronize versions across core POMs and infra manifests."""

    def register(self, subparsers: argparse._SubParsersAction) -> None:
        p_ver = subparsers.add_parser("version", help="Manage & sync versions across core POMs")
        ver_group = p_ver.add_mutually_exclusive_group()
        ver_group.add_argument(
            "--bump", choices=["major", "minor", "patch"], help="Bump version component"
        )
        ver_group.add_argument("--set", help="Set an explicit version string")
        p_ver.add_argument(
            "--dry-run", action="store_true", help="Show what would change without writing"
        )
        p_ver.set_defaults(handler=self.execute)

    def execute(self, cfg: ProjectConfig, args: argparse.Namespace) -> None:
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
            
            # Show infra manifests status
            print(f"\n  {_C.bold('Infra Manifests')}:")
            infra_dir = cfg.project_root / "infra"
            pattern = re.compile(r"image:\s*ghcr\.io/amocna-kr/([\w-]+):([0-9a-zA-Z.-]+)")
            infra_status = {}
            for path in sorted(infra_dir.rglob("*.yaml")) + sorted(infra_dir.rglob("*.yml")):
                if not path.is_file():
                    continue
                content = path.read_text()
                matches = pattern.findall(content)
                for img_name, tag in matches:
                    infra_status[img_name] = tag
                    
            if infra_status:
                for img_name, tag in sorted(infra_status.items()):
                    match = (
                        _C.green("✔ in sync")
                        if tag == current
                        else _C.red(f"✖ out of sync ({tag})")
                    )
                    print(f"    {img_name:<18}: {match}")
            else:
                print("    No ghcr.io/amocna-kr images found in infra")
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
            
            infra_updates = self._sync_infra_versions(cfg.project_root / "infra", new_version, dry_run=True)
            for path, old, new in infra_updates:
                rel_path = path.relative_to(cfg.project_root)
                print(f"  Would update: {_C.bold('infra image tag'):<25} {_C.dim(str(rel_path))} ({old} → {new})")
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

        # Apply infra updates
        infra_updates = self._sync_infra_versions(cfg.project_root / "infra", new_version, dry_run=False)
        for path, old, new in infra_updates:
            rel_path = path.relative_to(cfg.project_root)
            info(f"Updated infra image tag in {rel_path}: {old} → {new}")

        print()
        info(f"All core POMs and infra manifests synchronized to {_C.bold(new_version)}")
        print(
            f"  {_C.dim('Tip: run')} ./amocna.py version {_C.dim('to verify sync state')}"
        )
        print()

    def _sync_infra_versions(self, infra_dir: Path, new_version: str, dry_run: bool = False) -> list[tuple[Path, str, str]]:
        """Scan all yaml files in infra_dir and replace ghcr.io/amocna-kr/<app>:<tag> with new_version.
        Returns a list of tuples: (file_path, old_version, new_version) for files that were / would be updated.
        """
        updated = []
        pattern = re.compile(r"(image:\s*ghcr\.io/amocna-kr/[\w-]+:)([0-9a-zA-Z.-]+)")
        
        # scan both .yaml and .yml files
        for suffix in ("*.yaml", "*.yml"):
            for path in infra_dir.rglob(suffix):
                if not path.is_file():
                    continue
                content = path.read_text()
                matches = pattern.findall(content)
                if not matches:
                    continue
                    
                needs_update = False
                old_version = None
                for prefix, tag in matches:
                    if tag != new_version:
                        needs_update = True
                        old_version = tag
                        break
                        
                if needs_update:
                    new_content = pattern.sub(r"\g<1>" + new_version, content)
                    if not dry_run:
                        path.write_text(new_content)
                    actual_old = old_version or matches[0][1]
                    updated.append((path, actual_old, new_version))
                    
        return updated
