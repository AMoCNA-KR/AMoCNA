from __future__ import annotations

import sys
from typing import Optional
from typing_extensions import Annotated
import typer

from amocna_cli.config import ProjectConfig
from amocna_cli.utils.ui import console, header, info, error, run
from amocna_cli.utils.shell import k8s_port_forward

app = typer.Typer()

@app.callback(invoke_without_command=True)
def forward_cmd(
    ctx: typer.Context,
    name: Annotated[str, typer.Argument(help="Forward target name")],
    local_port: Annotated[Optional[int], typer.Option("--local-port", help="Override local port")] = None,
):
    """Port-forward a K8s service to localhost."""
    if ctx.invoked_subcommand is not None:
        return

    cfg: ProjectConfig = ctx.obj
    if name not in cfg.forwards:
        available = ", ".join(cfg.forwards.keys())
        error(f"Unknown forward target: {name}")
        error(f"Available: {available}")
        sys.exit(1)

    fwd = cfg.forwards[name]
    resolved_local_port = local_port or fwd.local_port

    header(f"Forwarding {name}")
    info(
        f"http://localhost:{resolved_local_port} → {fwd.namespace}/{fwd.service}:{fwd.remote_port}"
    )
    console.print("  [dim]Press Ctrl+C to stop.[/dim]\n")

    try:
        run(k8s_port_forward(fwd.namespace, fwd.service, resolved_local_port, fwd.remote_port))
    except KeyboardInterrupt:
        console.print()
        info("Forwarding stopped.")
