"""Exercise the actual new migration SQL on SQLite, preserving representative old rows.
This complements device Room validation; it does not replace it.
"""
from pathlib import Path
import re
import sqlite3

source=(Path(__file__).resolve().parents[1]/'app/src/main/java/com/sergey/reader/data/db/ReaderDatabase.kt').read_text()
section=source.split('val MIGRATION_4_5 =',1)[1].split('fun get(context:',1)[0]
statements=re.findall(r'db.execSQL\("([^"\n]+)"\)',section)
assert len(statements)==5
with sqlite3.connect(':memory:') as db:
    db.execute('PRAGMA foreign_keys=ON')
    db.execute('CREATE TABLE books(id INTEGER PRIMARY KEY, positionBlock INTEGER, positionOffset INTEGER)')
    db.execute('CREATE TABLE annotations(id INTEGER PRIMARY KEY, bookId INTEGER, selectedText TEXT)')
    db.execute('INSERT INTO books VALUES(1,42,17)')
    db.execute("INSERT INTO annotations VALUES(10,1,'Русский שלום 😀')")
    for sql in statements: db.execute(sql)
    assert db.execute('SELECT * FROM books').fetchone()==(1,42,17)
    assert db.execute('SELECT selectedText FROM annotations').fetchone()[0]=='Русский שלום 😀'
    db.execute('INSERT INTO document_positions VALUES(1,26,.72,.33,12.5,123)')
    db.execute("INSERT INTO document_marks VALUES(10,1,26,'[[1,2,3,4]]',1711276031)")
    assert db.execute('SELECT zoom FROM document_positions').fetchone()[0]==12.5
    db.execute('DELETE FROM annotations WHERE id=10')
    assert db.execute('SELECT COUNT(*) FROM document_marks').fetchone()[0]==0
    db.execute('DELETE FROM books WHERE id=1')
    assert db.execute('SELECT COUNT(*) FROM document_positions').fetchone()[0]==0
    assert not db.execute('PRAGMA foreign_key_check').fetchall()
print('PASS: migration 4→5 SQL preserves old rows/Unicode, saves viewport, cascades new rows')
