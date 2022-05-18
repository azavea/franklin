#!/usr/bin/env python

import os
from pathlib import Path

from pypgstac.db import PgstacDB
from pypgstac.load import Loader, Methods

from psycopg.conninfo import make_conninfo


DATA_DIR = os.path.join(Path(os.path.dirname(__file__)).parent.parent, "data-files")
collection = os.path.join(DATA_DIR, "naip/collection.json")
items = os.path.join(DATA_DIR, "naip/items")

dbhost = os.getenv("PGHOST") or "pgstac"
dbname = os.getenv("PGDATABASE") or "pgstac"
dbport = os.getenv("PGPORT") or 5432
dbuser = os.getenv("PGUSER") or "franklin"
dbpass = os.getenv("PGPASSWORD") or "franklin"

conninfo = make_conninfo("postgres://u@h/d", user=dbuser, host=dbhost, port=dbport, dbname=dbname, password=dbpass)
print(f"CONNINFO, {conninfo}")
db = PgstacDB(conninfo)
print("Updating franklin role settings")

with db.connect() as conn:
    cur = conn.cursor()
    cur.execute("ALTER ROLE franklin SET SEARCH_PATH to pgstac, '$user', public;")

with db.connect() as conn:
    cur = conn.cursor()
    cur.execute("ALTER ROLE franklin SET pgstac.context TO 'on';")


loader = Loader(db)

print("Loading collections...")
# Load naip collection
loader.load_collections(
    str(collection),
    insert_mode=Methods.upsert,
)

print("Loading items...")
# Load naip items
item_path_list = Path(items).glob("*.json")
for item_path in item_path_list:
    loader.load_items(
        str(item_path),
        insert_mode=Methods.upsert,
    )

print("Finished loading data.")