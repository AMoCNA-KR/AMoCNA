from abc import ABC, abstractmethod
import argparse
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from cli.core import ProjectConfig

class BasePlugin(ABC):
    """Abstract Base Class defining the contract for all AMoCNA CLI subcommand plugins."""

    @abstractmethod
    def register(self, subparsers: argparse._SubParsersAction) -> None:
        """Register subcommand parsers and their specific options."""
        pass

    @abstractmethod
    def execute(self, cfg: "ProjectConfig", args: argparse.Namespace) -> None:
        """Execute subcommand logic."""
        pass
