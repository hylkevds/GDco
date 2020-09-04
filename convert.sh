#!/bin/sh
echo "Step 1: HTML Parsing"
java -jar target/DotGen-1.0-SNAPSHOT-jar-with-dependencies.jar OGCOMV3Draft.html OGCOMV3Draft.dot
echo "Step 2: Dot"
dot -Tpng OGCOMV3Draft.dot -o OGCOMV3Draft.png
echo "Step 3: Done"
