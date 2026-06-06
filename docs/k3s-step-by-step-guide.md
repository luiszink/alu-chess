# K3S Schritt-fuer-Schritt Deployment Anleitung

Diese Anleitung ist fuer einen frischen Ubuntu-Server mit 30 GB RAM gedacht. Die Kubernetes-YAML-Dateien liegen bereits im Repo unter `deploy/k8s/`; du musst sie auf dem Server nicht mehr manuell erzeugen.

Ziel:

- K3S auf dem Server installieren
- Backend- und Frontend-Repos auf den Server laden
- App-Images direkt auf dem Server bauen
- App-Images in K3S/containerd importieren
- Kubernetes-Deployment mit Kustomize ausrollen
- Zugriff ueber `http://<serveradresse>:30080`
- optionaler Lichess-Bot-Service unter `/api/lichess/`

Wichtig:

- K3S verwendet containerd als Runtime, nicht Docker.
- Docker wird trotzdem installiert, damit du `docker build` und `docker save` auf dem Server nutzen kannst.
- Die App-Images werden lokal in K3S/containerd importiert; fuer diese Images brauchst du kein Docker-Hub-Secret.
- `mongo:7-jammy` wird weiterhin aus einer oeffentlichen Registry gezogen.
- Der Server braucht Internetzugang fuer `apt-get`, das K3S-Installationsscript, Docker-Build-Abhaengigkeiten und MongoDB.

## 0. Platzhalter

Auf deinem lokalen Rechner:

```bash
export SERVER_USER="<server-user>"
export SERVER_HOST="<serveradresse>"
```

Nach dem SSH-Login auf dem Server:

```bash
export TAG="0.1.0"
export BACKEND_REPO_URL="<git-url-zum-alu-chess-repo>"
export FRONTEND_REPO_URL="<git-url-zum-alu-chess-web-repo>"
export BACKEND_REPO_PATH="/opt/alu-chess/backend"
export FRONTEND_REPO_PATH="/opt/alu-chess/frontend"
```

Optionaler Tag mit Git-Commit:

```bash
export TAG="$(git rev-parse --short HEAD)"
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

## 2. Mit dem Server verbinden

VPN verbinden, dann:

```bash
ssh $SERVER_USER@$SERVER_HOST
```

Setze auf dem Server die Variablen:

```bash
export TAG="0.1.0"
export BACKEND_REPO_URL="<git-url-zum-alu-chess-repo>"
export FRONTEND_REPO_URL="<git-url-zum-alu-chess-web-repo>"
export BACKEND_REPO_PATH="/opt/alu-chess/backend"
export FRONTEND_REPO_PATH="/opt/alu-chess/frontend"
```

## 3. Server vorbereiten

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

Wenn `ufw` noch inaktiv ist, musst du es fuer das erste Deployment nicht aktivieren. Falls du es bewusst aktivieren willst:

```bash
sudo ufw enable
```

## 4. K3S installieren

```bash
curl -sfL https://get.k3s.io | sudo sh -s - --write-kubeconfig-mode 644
```

Status pruefen:

```bash
sudo systemctl status k3s --no-pager
sudo k3s kubectl get nodes
```

`kubectl` fuer deinen Benutzer einrichten:

```bash
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown "$USER:$USER" ~/.kube/config
kubectl get nodes
```

Falls `kubectl` nicht gefunden wird:

```bash
echo 'alias kubectl="sudo k3s kubectl"' >> ~/.bashrc
source ~/.bashrc
kubectl get nodes
```

## 5. Repos auf den Server laden

```bash
sudo mkdir -p /opt/alu-chess
sudo chown "$USER:$USER" /opt/alu-chess
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

Wenn die Repos privat sind, muss der Server vorher Zugriff haben, zum Beispiel ueber SSH-Key oder HTTPS-Token.

## 6. Kubernetes-Dateien pruefen

Die YAML-Dateien sind im Backend-Repo enthalten:

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

## 7. App-Images auf dem Server bauen

Backend-Images:

```bash
cd "$BACKEND_REPO_PATH"

sudo docker build -f Dockerfile.controller \
  -t localhost/alu-chess-controller:$TAG .

sudo docker build -f Dockerfile.model \
  -t localhost/alu-chess-model:$TAG .

sudo docker build -f Dockerfile.playerservice \
  -t localhost/alu-chess-playerservice:$TAG .

sudo docker build -f Dockerfile.stockfish \
  -t localhost/alu-chess-stockfish:$TAG .

sudo docker build -f Dockerfile.lichess \
  -t localhost/alu-chess-lichess:$TAG .
```

Frontend-Image:

```bash
cd "$FRONTEND_REPO_PATH"

sudo docker build -f Dockerfile.frontend \
  -t localhost/alu-chess-frontend:$TAG .
```

## 8. Images in K3S/containerd importieren

```bash
sudo docker save \
  localhost/alu-chess-controller:$TAG \
  localhost/alu-chess-model:$TAG \
  localhost/alu-chess-playerservice:$TAG \
  localhost/alu-chess-stockfish:$TAG \
  localhost/alu-chess-lichess:$TAG \
  localhost/alu-chess-frontend:$TAG \
  | sudo k3s ctr -n k8s.io images import -
```

Import pruefen:

```bash
sudo k3s ctr -n k8s.io images ls | grep alu-chess
sudo k3s crictl images | grep alu-chess
```

Dieser Schritt ist wichtig: Ein Image, das nur in Docker liegt, ist fuer K3S/containerd noch nicht sichtbar.

## 9. Image-Tag im Kustomize-Overlay setzen

```bash
cd "$BACKEND_REPO_PATH"
sed -i "s/newTag: .*/newTag: $TAG/g" deploy/k8s/overlays/prod/kustomization.yaml
```

Kontrollieren:

```bash
grep "newTag" deploy/k8s/overlays/prod/kustomization.yaml
```

## 10. Namespace und Secrets anlegen

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

Optional: Wenn der Lichess-Bot aktiv mit lichess.org verbunden werden soll, fuege den Bot-Token in dasselbe Secret ein:

```bash
kubectl create secret generic alu-chess-secrets \
  --namespace alu-chess \
  --from-literal=MONGO_USER="$MONGO_USER" \
  --from-literal=MONGO_PASSWORD="$MONGO_PASSWORD" \
  --from-literal=MONGO_URI="$MONGO_URI" \
  --from-literal=LICHESS_BOT_TOKEN="<lichess-bot-token>" \
  --dry-run=client -o yaml | kubectl apply -f -
```

Ohne `LICHESS_BOT_TOKEN` startet der Lichess-Service trotzdem, laeuft aber im deaktivierten Modus.

## 11. Deployment ausrollen

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

## 12. Fehleranalyse

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

## 13. Smoke-Checks

Vom Server:

```bash
curl http://localhost:30080/
curl http://localhost:30080/api/controller/state
curl http://localhost:30080/api/model/new-game
curl http://localhost:30080/api/model/stockfish/health
curl http://localhost:30080/api/lichess/status
```

Von deinem lokalen Rechner im VPN:

```bash
curl http://$SERVER_HOST:30080/
curl http://$SERVER_HOST:30080/api/controller/state
curl http://$SERVER_HOST:30080/api/model/new-game
curl http://$SERVER_HOST:30080/api/model/stockfish/health
curl http://$SERVER_HOST:30080/api/lichess/status
```

Browser:

```text
http://<serveradresse>:30080
```

## 14. Spiel testen

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

## 15. Neues Release deployen

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

sudo docker build -f Dockerfile.controller -t localhost/alu-chess-controller:$TAG .
sudo docker build -f Dockerfile.model -t localhost/alu-chess-model:$TAG .
sudo docker build -f Dockerfile.playerservice -t localhost/alu-chess-playerservice:$TAG .
sudo docker build -f Dockerfile.stockfish -t localhost/alu-chess-stockfish:$TAG .
sudo docker build -f Dockerfile.lichess -t localhost/alu-chess-lichess:$TAG .

cd "$FRONTEND_REPO_PATH"
sudo docker build -f Dockerfile.frontend -t localhost/alu-chess-frontend:$TAG .
```

Images importieren:

```bash
sudo docker save \
  localhost/alu-chess-controller:$TAG \
  localhost/alu-chess-model:$TAG \
  localhost/alu-chess-playerservice:$TAG \
  localhost/alu-chess-stockfish:$TAG \
  localhost/alu-chess-lichess:$TAG \
  localhost/alu-chess-frontend:$TAG \
  | sudo k3s ctr -n k8s.io images import -
```

Overlay aktualisieren und ausrollen:

```bash
cd "$BACKEND_REPO_PATH"
sed -i "s/newTag: .*/newTag: $TAG/g" deploy/k8s/overlays/prod/kustomization.yaml
kubectl apply -k deploy/k8s/overlays/prod
```

Wenn du denselben Tag erneut verwendest:

```bash
kubectl rollout restart deployment/frontend -n alu-chess
kubectl rollout restart deployment/controller -n alu-chess
kubectl rollout restart deployment/model -n alu-chess
kubectl rollout restart deployment/playerservice -n alu-chess
kubectl rollout restart deployment/stockfish -n alu-chess
kubectl rollout restart deployment/lichess -n alu-chess
```

## 16. MongoDB Backup

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

kubectl cp alu-chess/mongo-0:/tmp/chess.archive /opt/alu-chess/chess.archive
```

Vom lokalen Rechner herunterladen:

```bash
scp $SERVER_USER@$SERVER_HOST:/opt/alu-chess/chess.archive ./chess.archive
```

## 17. Anwendung stoppen oder entfernen

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

Alles inklusive PVC loeschen:

```bash
kubectl delete namespace alu-chess
```

## 18. K3S deinstallieren

Nur ausfuehren, wenn der ganze Cluster entfernt werden soll:

```bash
sudo /usr/local/bin/k3s-uninstall.sh
```

## 19. Hinweise

- `controller` und `playerservice` bleiben bei `replicas: 1`, weil aktive Spiele und Sessions im Memory liegen.
- Die App-Images liegen nur lokal auf diesem K3S-Node. Bei mehreren Nodes musst du sie auf jedem Node importieren oder wieder ueber eine Registry bereitstellen.
- MongoDB speichert abgeschlossene Spiele persistent auf dem K3S-Node.
- Lichess funktioniert ohne Token nur als gestarteter, aber nicht verbundener Service. Fuer echte Bot-Spiele muss `LICHESS_BOT_TOKEN` im Secret gesetzt werden.
- Bei Single-Node-K3S ist `local-path` Storage ausreichend, aber Backups sind wichtig.
- Der Zugriff erfolgt ohne TLS ueber `http://<serveradresse>:30080`.
