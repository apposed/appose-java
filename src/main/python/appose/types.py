import json
from typing import Any, Dict


Args = Dict[str, Any]


def encode(data: Args) -> str:
    return json.dumps(data)


def decode(the_json: str) -> Args:
    return json.loads(the_json)
