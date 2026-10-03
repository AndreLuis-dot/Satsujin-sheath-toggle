# Satsujin Sheath Toggle

Mod para **Minecraft 1.20.1 / Forge 47.x** que, junto com *Weapons of Miracles* + *Epic Fight*:

- **desliga** o auto-guardar da espada **Satsujin** (o timer de ~4 s da `SatsujinPassive`);
- adiciona a tecla **V** (configuravel em *Controles > Satsujin Sheath Toggle*) que alterna **guardar / sacar**.

Nao precisa dos .jar dos outros mods para compilar (usa reflexao).

## Gerar o .jar pelo GitHub (sem instalar nada)

1. Crie um repositorio (pode ser **privado**) e suba **todos** estes arquivos, mantendo as pastas
   (inclusive `.github/workflows/build.yml`).
2. Aba **Actions** > workflow **Build mod** (roda sozinho a cada push; ou clique em *Run workflow*).
3. Quando ficar verde, abra a execucao e baixe o artefato **sheathtoggle-jar** (um .zip com o `.jar`).
4. Coloque `sheathtoggle-1.0.0.jar` na pasta `mods` do modpack. Precisa estar tambem no servidor, se jogar em servidor.

## Compilar na sua maquina

Requer JDK 17 e Gradle 8.x instalado:

    gradle build

O jar sai em `build/libs/`. (Se preferir o wrapper: `gradle wrapper --gradle-version 8.8` uma vez, depois `./gradlew build`.)

## Se algo nao funcionar

Abra `logs/latest.log` e procure por `sheathtoggle`. O mod escreve la qual nome nao encontrou
no Epic Fight / Weapons of Miracles. Mande essa linha (ou o log do Actions, se o build falhar).

## Notas

- A tecla so age com a Satsujin na mao e fora de animacoes (`inaction`).
- A passiva original ainda saca a espada ao usar itens (comer, etc.); isso foi mantido.
- Se V conflitar com outra tecla do Epic Fight, mude em *Controles*.
