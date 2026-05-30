import argparse
import os
import subprocess
import sys
from cli.plugins.base import BasePlugin
from cli.core import ProjectConfig, header, info, error

def check_pat() -> str:
    """Ensure AMOCNA_PAT is set in the environment."""
    pat = os.environ.get("AMOCNA_PAT")
    if not pat:
        error("AMOCNA_PAT environment variable is not set.")
        error("Please set it: export AMOCNA_PAT=your_github_token")
        sys.exit(1)
    return pat

class LoginPlugin(BasePlugin):
    """Plugin to login to the Docker registry using AMoCNA_PAT."""

    def register(self, subparsers: argparse._SubParsersAction) -> None:
        p_login = subparsers.add_parser("login", help="Login to Docker registry using PAT")
        p_login.add_argument("--registry", help="Docker registry (overrides config & env)")
        p_login.add_argument("--user", help="GitHub username (overrides AMOCNA_USER env)")
        p_login.set_defaults(handler=self.execute)

    def execute(self, cfg: ProjectConfig, args: argparse.Namespace) -> None:
        registry = args.registry or cfg.registry
        pat = check_pat()
        user = args.user or os.environ.get("AMOCNA_USER")

        if not user:
            error("User not specified. Use --user or set AMOCNA_USER.")
            sys.exit(1)

        header(f"Logging in to {registry}")
        subprocess.run(
            ["docker", "login", registry, "--username", user, "--password-stdin"],
            input=pat,
            text=True,
            check=True,
        )
        info(f"Successfully logged in to {registry}")
