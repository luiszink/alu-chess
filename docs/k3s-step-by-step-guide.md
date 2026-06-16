# K3S mit k3d Schritt-fuer-Schritt Deployment Anleitung

Diese Anleitung beschreibt das Deployment von Alu Chess in einen lokalen oder
serverseitigen k3d-Cluster. k3d startet K3S in Docker-Containern. Du installierst
also keinen K3S-Systemdienst direkt auf dem Host, sondern erstellst einen
wegwerfbaren K3S-Cluster mit Docker als Unterbau.

Die K3S-Manifest-Dateien liegen bereits im Repo unter `deploy/k8s/`. Der Ordner
heisst historisch `k8s`, deployt wird hier aber K3S ueber k3d.

Ziel:

- K3S-Cluster mit k3d und Zugriff auf `http://localhost:30080` anlegen
- Backend- und Frontend-Images mit Docker bauen
- Images mit `k3d image import` in den Cluster laden
- K3S-Deployment mit den vorhandenen Manifesten ausrollen
- optionaler Zugriff von einem Server ueber `http://<serveradresse>:30080`
- optionaler Lichess-Bot-Service unter `/api/lichess/`

Wichtig:

- k3d braucht Docker und `kubectl`.
- `kubectl` spricht hier mit dem K3S-Cluster, den k3d startet.
- Die App-Images werden lokal gebaut und in den k3d-Cluster importiert.
- Fuer die lokalen `localhost/alu-chess-*` Images brauchst du kein Docker-Hub-Secret.
- `mongo:7-jammy` wird weiterhin aus einer oeffentlichen Registry gezogen.
- Die Manifeste nutzen `imagePullPolicy: Never`; ohne Image-Import starten die Pods
  deshalb nicht.
- Der Dateiname enthaelt noch `k3s`, die Schritte in dieser Datei sind aber fuer k3d.

## 0. Platzhalter

Auf dem Rechner, auf dem k3d laufen soll:

```bash
export TAG="0.1.0"
export CLUSTER_NAME="alu-chess"
export BACKEND_REPO_URL="<git-url-zum-alu-chess-repo>"
export FRONTEND_REPO_URL="<git-url-zum-alu-chess-web-repo>"
export BACKEND_REPO_PATH="$HOME/alu-chess/backend"
export FRONTEND_REPO_PATH="$HOME/alu-chess/frontend"
```

Optionaler Tag mit Git-Commit, wenn du im Backend-Repo stehst:

```bash
export TAG="$(git rev-parse --short HEAD)"
```

Falls du k3d auf einem entfernten Server betreibst, setze auf deinem lokalen
Rechner zusaetzlich:

```bash
export SERVER_USER="<server-user>"
export SERVER_HOST="<serveradresse>"
```

## 1. Lokal testen

Backend:

```bash
cd /pfad/zu/alu-chess
sbt test
```

Frontend:

```bash
cd /pfad/zu/alu-chess-web
npm install
npm run build
npm run lint
```

Optionaler Compose-Test im Backend-Repo:

```bash
docker compose --profile mongo up --build
docker compose down
```

## 2. Zielmaschine vorbereiten

Wenn du k3d lokal verwendest, fuehre die Befehle auf deinem Rechner aus. Wenn du
k3d auf einem Server verwenden willst, verbinde dich zuerst per SSH:

```bash
ssh $SERVER_USER@$SERVER_HOST
```

Ubuntu/Debian-Pakete:

```bash
sudo apt-get update
sudo apt-get upgrade -y

sudo apt-get install -y \
  ca-certificates \
  curl \
  git \
  openssl \
  ufw \
  docker.io
```

Docker starten:

```bash
sudo systemctl enable --now docker
sudo docker version
```

Damit du Docker ohne `sudo` nutzen kannst:

```bash
sudo usermod -aG docker "$USER"
newgrp docker
docker version
```

Speicher und Platte pruefen:

```bash
free -h
df -h
```

Port fuer die App freigeben, falls `ufw` aktiv ist oder aktiviert werden soll:

```bash
sudo ufw allow 22/tcp
sudo ufw allow 30080/tcp
sudo ufw status
```

Wenn `ufw` noch inaktiv ist, musst du es fuer das erste lokale Deployment nicht
aktivieren. Falls du es bewusst aktivieren willst:

```bash
sudo ufw enable
```

## 3. kubectl installieren

Wenn `kubectl` schon vorhanden ist:

```bash
kubectl version --client
```

Falls nicht, installiere die aktuelle Linux-x86-64-Version:

```bash
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
kubectl version --client
```

Auf ARM64 ersetze `amd64` durch `arm64`.

## 4. k3d installieren

Installieren:

```bash
curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash
k3d version
```

Alternative unter macOS/Linux mit Homebrew:

```bash
brew install k3d
```

Alternative unter Windows:

```powershell
choco install k3d
# oder:
scoop install k3d
```

Docker muss laufen, bevor du einen k3d-Cluster erstellst.

## 5. k3d-Cluster anlegen

Der Frontend-Service ist ein `NodePort` mit Port `30080`. Deshalb wird beim
Cluster-Start genau dieser Port aus dem k3d-Node auf den Host gemappt:

```bash
k3d cluster create "$CLUSTER_NAME" \
  --agents 1 \
  -p "30080:30080@agent:0"
```

Pruefen:

```bash
k3d cluster list
kubectl config current-context
kubectl get nodes
```

Wenn du den Cluster auf einem entfernten Server betreibst und der Port explizit
auf allen Interfaces liegen soll:

```bash
k3d cluster delete "$CLUSTER_NAME"
k3d cluster create "$CLUSTER_NAME" \
  --agents 1 \
  -p "0.0.0.0:30080:30080@agent:0"
```

Danach ist die App spaeter unter `http://<serveradresse>:30080` erreichbar,
sofern Firewall und Netzwerk den Port erlauben.

## 6. Repos laden

```bash
mkdir -p "$HOME/alu-chess"
```

Backend-Repo:

```bash
git clone "$BACKEND_REPO_URL" "$BACKEND_REPO_PATH"
cd "$BACKEND_REPO_PATH"
```

Frontend-Repo:

```bash
git clone "$FRONTEND_REPO_URL" "$FRONTEND_REPO_PATH"
cd "$FRONTEND_REPO_PATH"
```

Falls die Repos schon existieren:

```bash
cd "$BACKEND_REPO_PATH"
git pull

cd "$FRONTEND_REPO_PATH"
git pull
```

Wenn die Repos privat sind, muss die Zielmaschine vorher Zugriff haben, zum
Beispiel ueber SSH-Key oder HTTPS-Token.

## 7. K3S-Manifeste pruefen

Die Manifest-Dateien sind im Backend-Repo enthalten:

```bash
cd "$BACKEND_REPO_PATH"
ls deploy/k8s/base
ls deploy/k8s/overlays/prod
```

Erwartete Dateien:

```text
deploy/k8s/base/
  namespace.yaml
  mongo.yaml
  stockfish.yaml
  model.yaml
  playerservice.yaml
  controller.yaml
  lichess.yaml
  frontend.yaml
  kustomization.yaml

deploy/k8s/overlays/prod/
  kustomization.yaml
```

## 8. App-Images bauen

Backend-Images:

```bash
cd "$BACKEND_REPO_PATH"

docker build -f Dockerfile.controller \
  -t localhost/alu-chess-controller:$TAG .

docker build -f Dockerfile.model \
  -t localhost/alu-chess-model:$TAG .

docker build -f Dockerfile.playerservice \
  -t localhost/alu-chess-playerservice:$TAG .

docker build -f Dockerfile.stockfish \
  -t localhost/alu-chess-stockfish:$TAG .

docker build -f Dockerfile.lichess \
  -t localhost/alu-chess-lichess:$TAG .
```

Frontend-Image:

```bash
cd "$FRONTEND_REPO_PATH"

docker build -f Dockerfile.frontend \
  -t localhost/alu-chess-frontend:$TAG .
```

Images auf dem Host pruefen:

```bash
docker images "localhost/alu-chess-*"
```

## 9. Images in k3d importieren

```bash
k3d image import \
  localhost/alu-chess-controller:$TAG \
  localhost/alu-chess-model:$TAG \
  localhost/alu-chess-playerservice:$TAG \
  localhost/alu-chess-stockfish:$TAG \
  localhost/alu-chess-lichess:$TAG \
  localhost/alu-chess-frontend:$TAG \
  -c "$CLUSTER_NAME"
```

Dieser Schritt ist wichtig: Ein Image, das nur im Host-Docker liegt, ist fuer die
K3S-Nodes im k3d-Cluster noch nicht automatisch sichtbar.

Import grob pruefen:

```bash
docker exec k3d-${CLUSTER_NAME}-agent-0 crictl images | grep alu-chess
```

Falls dein Cluster ohne Agent angelegt wurde, pruefe stattdessen den Server-Node:

```bash
docker exec k3d-${CLUSTER_NAME}-server-0 crictl images | grep alu-chess
```

## 10. Image-Tag im Overlay setzen

```bash
cd "$BACKEND_REPO_PATH"
sed -i "s/newTag: .*/newTag: $TAG/g" deploy/k8s/overlays/prod/kustomization.yaml
```

Kontrollieren:

```bash
grep "newTag" deploy/k8s/overlays/prod/kustomization.yaml
```

## 11. Namespace und Secrets anlegen

Namespace:

```bash
cd "$BACKEND_REPO_PATH"
kubectl apply -f deploy/k8s/base/namespace.yaml
```

MongoDB-Zugangsdaten erzeugen:

```bash
export MONGO_USER="chess"
export MONGO_PASSWORD="$(openssl rand -base64 24 | tr -d '\n')"
export MONGO_URI="mongodb://$MONGO_USER:$MONGO_PASSWORD@mongo:27017/chess?authSource=admin"
```

Secret anlegen oder aktualisieren:

```bash
kubectl create secret generic alu-chess-secrets \
  --namespace alu-chess \
  --from-literal=MONGO_USER="$MONGO_USER" \
  --from-literal=MONGO_PASSWORD="$MONGO_PASSWORD" \
  --from-literal=MONGO_URI="$MONGO_URI" \
  --dry-run=client -o yaml | kubectl apply -f -
```

Optional: Wenn der Lichess-Bot aktiv mit lichess.org verbunden werden soll,
fuege den Bot-Token in dasselbe Secret ein:

```bash
kubectl create secret generic alu-chess-secrets \
  --namespace alu-chess \
  --from-literal=MONGO_USER="$MONGO_USER" \
  --from-literal=MONGO_PASSWORD="$MONGO_PASSWORD" \
  --from-literal=MONGO_URI="$MONGO_URI" \
  --from-literal=LICHESS_BOT_TOKEN="<lichess-bot-token>" \
  --dry-run=client -o yaml | kubectl apply -f -
```

Ohne `LICHESS_BOT_TOKEN` startet der Lichess-Service trotzdem, laeuft aber im
deaktivierten Modus.

## 12. Deployment ausrollen

```bash
cd "$BACKEND_REPO_PATH"
kubectl apply -k deploy/k8s/overlays/prod
```

Rollout pruefen:

```bash
kubectl rollout status statefulset/mongo -n alu-chess
kubectl rollout status deployment/stockfish -n alu-chess
kubectl rollout status deployment/model -n alu-chess
kubectl rollout status deployment/playerservice -n alu-chess
kubectl rollout status deployment/controller -n alu-chess
kubectl rollout status deployment/lichess -n alu-chess
kubectl rollout status deployment/frontend -n alu-chess
```

Ressourcen anzeigen:

```bash
kubectl get pods -n alu-chess
kubectl get svc -n alu-chess
kubectl get pvc -n alu-chess
```

## 13. Smoke-Checks

Vom k3d-Host:

```bash
curl http://localhost:30080/
curl http://localhost:30080/api/controller/state
curl http://localhost:30080/api/model/new-game
curl http://localhost:30080/api/model/stockfish/health
curl http://localhost:30080/api/lichess/status
```

Von deinem lokalen Rechner, wenn k3d auf einem entfernten Server laeuft:

```bash
curl http://$SERVER_HOST:30080/
curl http://$SERVER_HOST:30080/api/controller/state
curl http://$SERVER_HOST:30080/api/model/new-game
curl http://$SERVER_HOST:30080/api/model/stockfish/health
curl http://$SERVER_HOST:30080/api/lichess/status
```

Browser lokal:

```text
http://localhost:30080
```

Browser bei Serverbetrieb:

```text
http://<serveradresse>:30080
```

## 14. Fehleranalyse

Logs:

```bash
kubectl logs -n alu-chess deployment/frontend
kubectl logs -n alu-chess deployment/controller
kubectl logs -n alu-chess deployment/model
kubectl logs -n alu-chess deployment/playerservice
kubectl logs -n alu-chess deployment/stockfish
kubectl logs -n alu-chess deployment/lichess
kubectl logs -n alu-chess statefulset/mongo
```

Pod-Details:

```bash
kubectl describe pod -n alu-chess <pod-name>
```

Events:

```bash
kubectl get events -n alu-chess --sort-by=.metadata.creationTimestamp
```

Typische Probleme:

- `ErrImageNeverPull`: Image wurde nicht mit `k3d image import` importiert oder
  der Tag im Overlay passt nicht.
- `localhost:30080` nicht erreichbar: Cluster wurde ohne Port-Mapping erstellt
  oder der Host-Port ist schon belegt.
- `ImagePullBackOff` bei MongoDB: Die Zielmaschine hat keinen Registry- oder
  Internetzugang fuer `mongo:7-jammy`.
- `permission denied` bei Docker: Benutzer ist noch nicht in der Gruppe `docker`
  oder die Shell wurde nach `usermod` nicht neu geladen.

## 15. Spiel testen

1. App im Browser oeffnen.
2. Neues Spiel starten.
3. Einen Zug ausfuehren, zum Beispiel `e2 -> e4`.
4. Stockfish-Analyse pruefen.
5. Spiel beenden.
6. Pruefen, ob MongoDB erreichbar ist.

MongoDB-Shell:

```bash
export MONGO_USER="$(kubectl get secret alu-chess-secrets -n alu-chess -o jsonpath='{.data.MONGO_USER}' | base64 -d)"
export MONGO_PASSWORD="$(kubectl get secret alu-chess-secrets -n alu-chess -o jsonpath='{.data.MONGO_PASSWORD}' | base64 -d)"

kubectl exec -it -n alu-chess mongo-0 -- \
  mongosh -u "$MONGO_USER" -p "$MONGO_PASSWORD" --authenticationDatabase admin chess
```

In der Mongo-Shell:

```javascript
show collections
db.getCollectionNames()
exit
```

## 16. Neues Release deployen

Neuen Tag setzen:

```bash
export TAG="<neuer-tag>"
```

Repos aktualisieren:

```bash
cd "$BACKEND_REPO_PATH"
git pull

cd "$FRONTEND_REPO_PATH"
git pull
```

Images neu bauen:

```bash
cd "$BACKEND_REPO_PATH"

docker build -f Dockerfile.controller -t localhost/alu-chess-controller:$TAG .
docker build -f Dockerfile.model -t localhost/alu-chess-model:$TAG .
docker build -f Dockerfile.playerservice -t localhost/alu-chess-playerservice:$TAG .
docker build -f Dockerfile.stockfish -t localhost/alu-chess-stockfish:$TAG .
docker build -f Dockerfile.lichess -t localhost/alu-chess-lichess:$TAG .

cd "$FRONTEND_REPO_PATH"
docker build -f Dockerfile.frontend -t localhost/alu-chess-frontend:$TAG .
```

Images importieren:

```bash
k3d image import \
  localhost/alu-chess-controller:$TAG \
  localhost/alu-chess-model:$TAG \
  localhost/alu-chess-playerservice:$TAG \
  localhost/alu-chess-stockfish:$TAG \
  localhost/alu-chess-lichess:$TAG \
  localhost/alu-chess-frontend:$TAG \
  -c "$CLUSTER_NAME"
```

Overlay aktualisieren und ausrollen:

```bash
cd "$BACKEND_REPO_PATH"
sed -i "s/newTag: .*/newTag: $TAG/g" deploy/k8s/overlays/prod/kustomization.yaml
kubectl apply -k deploy/k8s/overlays/prod
```

Wenn du denselben Tag erneut verwendest, importiere die Images erneut und starte
die Deployments neu:

```bash
kubectl rollout restart deployment/frontend -n alu-chess
kubectl rollout restart deployment/controller -n alu-chess
kubectl rollout restart deployment/model -n alu-chess
kubectl rollout restart deployment/playerservice -n alu-chess
kubectl rollout restart deployment/stockfish -n alu-chess
kubectl rollout restart deployment/lichess -n alu-chess
```

## 17. MongoDB Backup

```bash
export MONGO_USER="$(kubectl get secret alu-chess-secrets -n alu-chess -o jsonpath='{.data.MONGO_USER}' | base64 -d)"
export MONGO_PASSWORD="$(kubectl get secret alu-chess-secrets -n alu-chess -o jsonpath='{.data.MONGO_PASSWORD}' | base64 -d)"

kubectl exec -n alu-chess mongo-0 -- \
  mongodump \
  -u "$MONGO_USER" \
  -p "$MONGO_PASSWORD" \
  --authenticationDatabase admin \
  --db chess \
  --archive=/tmp/chess.archive

kubectl cp alu-chess/mongo-0:/tmp/chess.archive ./chess.archive
```

Wenn k3d auf einem entfernten Server laeuft, kannst du das Backup danach lokal
herunterladen:

```bash
scp $SERVER_USER@$SERVER_HOST:~/chess.archive ./chess.archive
```

## 18. Anwendung stoppen oder entfernen

Pods stoppen, aber Deployments, Services und PVC behalten:

```bash
kubectl scale deployment/frontend --replicas=0 -n alu-chess
kubectl scale deployment/controller --replicas=0 -n alu-chess
kubectl scale deployment/model --replicas=0 -n alu-chess
kubectl scale deployment/playerservice --replicas=0 -n alu-chess
kubectl scale deployment/stockfish --replicas=0 -n alu-chess
kubectl scale deployment/lichess --replicas=0 -n alu-chess
kubectl scale statefulset/mongo --replicas=0 -n alu-chess
```

Wieder starten:

```bash
kubectl scale statefulset/mongo --replicas=1 -n alu-chess
kubectl scale deployment/stockfish --replicas=1 -n alu-chess
kubectl scale deployment/model --replicas=1 -n alu-chess
kubectl scale deployment/playerservice --replicas=1 -n alu-chess
kubectl scale deployment/controller --replicas=1 -n alu-chess
kubectl scale deployment/lichess --replicas=1 -n alu-chess
kubectl scale deployment/frontend --replicas=1 -n alu-chess
```

K3S-Ressourcen inklusive PVC loeschen:

```bash
kubectl delete namespace alu-chess
```

Ganzen k3d-Cluster loeschen:

```bash
k3d cluster delete "$CLUSTER_NAME"
```

## 19. Hinweise

- `controller` und `playerservice` bleiben bei `replicas: 1`, weil aktive Spiele
  und Sessions im Memory liegen.
- Die App-Images liegen nur im k3d-Cluster. Nach `k3d cluster delete` musst du sie
  fuer einen neuen Cluster wieder importieren.
- MongoDB speichert abgeschlossene Spiele persistent in einem k3d/Docker-Volume.
  Loescht du den ganzen Cluster, pruefe vorher, ob du ein Backup brauchst.
- Lichess funktioniert ohne Token nur als gestarteter, aber nicht verbundener
  Service. Fuer echte Bot-Spiele muss `LICHESS_BOT_TOKEN` im Secret gesetzt werden.
- Der Zugriff erfolgt ohne TLS ueber `http://localhost:30080` oder bei Serverbetrieb
  ueber `http://<serveradresse>:30080`.
- Eine Registry ist fuer dieses Setup nicht noetig. Wenn spaeter mehrere Nodes,
  CI/CD oder Argo CD dazukommen, ist eine lokale oder externe Registry sauberer als
  wiederholtes `k3d image import`.

## 20. Quellen

- k3d: https://k3d.io/stable/
- k3d Services exponieren: https://k3d.io/stable/usage/exposing_services/
- k3d Images importieren: https://k3d.io/stable/usage/commands/k3d_image_import/
- kubectl installieren: https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/
