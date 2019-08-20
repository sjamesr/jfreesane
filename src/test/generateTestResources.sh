#!/bin/bash

set -xe

mkdir -p src/test/resources

for mode in "Gray" "Color"; do
  for depth in 1 8 16; do
    for pattern in "Solid white" "Solid black" "Color pattern"; do
      if [[ "${depth}" != 1 || "${mode}" != "Color" ]]; then
        filename="src/test/resources/${mode}-${depth}-${pattern}.png"
        filename="${filename// /_}"
        scanimage -d test --format=png --mode "${mode}" --depth "${depth}" --test-picture "${pattern}" > $filename
      fi
    done
  done
done

echo "SANE reference images generated on $(date) by:" > src/test/resources/README
echo -e "\n" >> src/test/resources/README
scanimage --version >> src/test/resources/README
echo -e "\n\nTo regenerate, run $0 from the project root directory." >> src/test/resources/README
