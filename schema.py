import sqlite3
conn = sqlite3.connect('app/src/main/assets/bdd.db')
for row in conn.execute("SELECT sql FROM sqlite_master WHERE type='table'"):
    print(row[0])
