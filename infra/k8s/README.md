# Kubernetes deployment

Create the runtime secret on the server; never commit the real file:

```bash
cp .env.production.example .env.production
vi .env.production
kubectl create namespace vap --dry-run=client -o yaml | kubectl apply -f -
kubectl create secret generic vap-secrets -n vap --from-env-file=.env.production --dry-run=client -o yaml | kubectl apply -f -
sed -i "s/YOUR_DOCKERHUB_USERNAME/${DOCKER_USERNAME}/g" backend.yaml ai.yaml
kubectl apply -k .
kubectl rollout status deployment/backend -n vap
kubectl rollout status deployment/ai -n vap
```

The AI service is internal-only. Traefik exposes only the backend on port 80.
MySQL is hosted by private RDS. Replace `RDS_ENDPOINT` in `configmap.yaml` with
the `terraform output -raw rds_endpoint` value before applying the manifests.
