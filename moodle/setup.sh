#!/usr/bin/env bash
# Executa a configuração do Moodle local dentro do contêiner.
set -euo pipefail
cd "$(dirname "$0")/.."
# Impede o Git Bash (Windows) de converter /tmp em um caminho do Windows
export MSYS_NO_PATHCONV=1

docker compose cp moodle/setup.php moodle:/tmp/setup.php
docker compose exec -T moodle php /tmp/setup.php
