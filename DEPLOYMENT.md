# Deployment Guide - Solace Schema Registry on AWS EKS

This guide covers deploying Solace Schema Registry on AWS EKS for development and testing purposes.

## Overview

The deployment creates:
- AWS EKS cluster with managed node group
- ECR repositories for Schema Registry container images
- PostgreSQL database using CloudNativePG operator
- Solace Schema Registry deployed via Helm
- NGINX Ingress Controller with TLS

## Prerequisites

- AWS CLI configured with appropriate credentials
- kubectl installed
- Helm 3.x installed
- Docker or Podman for container operations
- OpenSSL for certificate generation

## Architecture

```
Internet → AWS ELB → NGINX Ingress → Schema Registry (3 pods)
                                            ↓
                                      PostgreSQL (CloudNativePG)
                                            ↓
                                      EBS Volumes (gp2)
```

## Step 1: Deploy AWS Infrastructure

### 1.1 Create EKS Cluster

```bash
cd infra/

# Deploy EKS cluster (takes ~20-25 minutes)
aws cloudformation create-stack \
  --stack-name solace-schema-registry-eks \
  --template-body file://eks-cluster.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --region us-east-2

# Wait for completion
aws cloudformation wait stack-create-complete \
  --stack-name solace-schema-registry-eks \
  --region us-east-2

# Configure kubectl
aws eks update-kubeconfig \
  --name sr-eks \
  --region us-east-2
```

### 1.2 Create ECR Repositories

```bash
# Deploy ECR repositories for Schema Registry images
aws cloudformation create-stack \
  --stack-name schema-registry-ecr \
  --template-body file://schema-registry-ecr.yaml \
  --region us-east-2

# Wait for completion
aws cloudformation wait stack-create-complete \
  --stack-name schema-registry-ecr \
  --region us-east-2
```

## Step 2: Upload Schema Registry Images

### 2.1 Download Schema Registry Images

Download the Solace Schema Registry images from Solace (requires account):
- `solace-registry-v1.0.0.tar.gz`
- `solace-registry-ui-v1.0.0.tar.gz`
- `solace-schema-registry-login-v1.0.0.tar.gz`

Place them in the `docker-images/` directory.

### 2.2 Upload to ECR

```bash
# Set your AWS account ID and region
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export AWS_REGION=us-east-2

# Authenticate to ECR
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin \
  $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Use the upload script
cd infra/scripts/
chmod +x upload_image.sh
./upload_image.sh

# Or manually for each image:
cd ../../docker-images/

# Backend
gunzip -c solace-registry-v1.0.0.tar.gz > backend.tar
docker load -i backend.tar
docker tag <IMAGE_ID> $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/your-org/schemaregistry/solace-registry-v1.0.0:v1.0.0
docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/your-org/schemaregistry/solace-registry-v1.0.0:v1.0.0

# UI
gunzip -c solace-registry-ui-v1.0.0.tar.gz > ui.tar
docker load -i ui.tar
docker tag <IMAGE_ID> $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/your-org/schemaregistry/solace-registry-ui-v1.0.0:v1.0.0
docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/your-org/schemaregistry/solace-registry-ui-v1.0.0:v1.0.0

# Login/IDP
gunzip -c solace-schema-registry-login-v1.0.0.tar.gz > login.tar
docker load -i login.tar
docker tag <IMAGE_ID> $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/your-org/schemaregistry/solace-schema-registry-login-v1.0.0:v1.0.0
docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/your-org/schemaregistry/solace-schema-registry-login-v1.0.0:v1.0.0
```

## Step 3: Install Kubernetes Components

### 3.1 Install CloudNativePG Operator

```bash
# Add Helm repository
helm repo add cnpg https://cloudnative-pg.github.io/charts
helm repo update

# Install operator
helm install cnpg-operator cnpg/cloudnative-pg \
  -n cnpg-system \
  --create-namespace
```

### 3.2 Install NGINX Ingress Controller

```bash
# Add Helm repository
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update

# Install ingress controller
helm install ingress-nginx ingress-nginx/ingress-nginx \
  -n ingress-nginx \
  --create-namespace

# Get load balancer IP/hostname
kubectl get svc ingress-nginx-controller -n ingress-nginx

# Wait for EXTERNAL-IP to be assigned
kubectl get svc ingress-nginx-controller -n ingress-nginx -w
```

### 3.3 Create TLS Certificate

```bash
# Get the load balancer IP
LB_HOST=$(kubectl get svc ingress-nginx-controller -n ingress-nginx -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
LB_IP=$(dig +short "$LB_HOST" | head -1)

echo "Load Balancer IP: $LB_IP"

# Generate self-signed certificate for *.LB_IP.nip.io
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout tls.key -out tls.crt \
  -subj "/CN=*.$LB_IP.nip.io" \
  -addext "subjectAltName=DNS:*.$LB_IP.nip.io,DNS:$LB_IP.nip.io"

# Create namespace
kubectl create namespace solace

# Create TLS secret
kubectl -n solace create secret tls schema-registry-tls-secret \
  --cert=tls.crt \
  --key=tls.key
```

**Note**: For production, use a CA-signed certificate instead of self-signed.

## Step 4: Configure Helm Values

### 4.1 Copy Template

```bash
cd infra/
cp values-override.yaml.example values-override.yaml
```

### 4.2 Edit Values

Update `values-override.yaml` with your settings:

```yaml
ingress:
  hostNameSuffix: "YOUR_LB_IP.nip.io"  # Replace with actual IP

idp:
  developerPassword: "YOUR_PASSWORD"    # Change from default
  readonlyPassword: "YOUR_PASSWORD"     # Change from default
  image:
    name: "YOUR_ACCOUNT.dkr.ecr.YOUR_REGION.amazonaws.com/your-org/schemaregistry/solace-schema-registry-login-v1.0.0"

backend:
  image:
    name: "YOUR_ACCOUNT.dkr.ecr.YOUR_REGION.amazonaws.com/your-org/schemaregistry/solace-registry-v1.0.0"

ui:
  image:
    name: "YOUR_ACCOUNT.dkr.ecr.YOUR_REGION.amazonaws.com/your-org/schemaregistry/solace-registry-ui-v1.0.0"
```

### 4.3 Create Image Pull Secret

```bash
# Create Docker registry secret for ECR
kubectl -n solace create secret docker-registry docker-registry-config \
  --docker-server=$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com \
  --docker-username=AWS \
  --docker-password=$(aws ecr get-login-password --region $AWS_REGION)

# Label the secret for Helm
kubectl -n solace label secret docker-registry-config \
  app.kubernetes.io/managed-by=Helm

kubectl -n solace annotate secret docker-registry-config \
  meta.helm.sh/release-name=schema-registry \
  meta.helm.sh/release-namespace=solace
```

## Step 5: Install Schema Registry

### 5.1 Download Helm Chart

Download the Solace Schema Registry Helm chart from Solace and extract it.

### 5.2 Install with Helm

```bash
# Set chart path
CHART="/path/to/solace-schema-registry-1.0.0.tgz"

# Install Schema Registry
helm upgrade --install schema-registry "$CHART" \
  -n solace \
  -f values-override.yaml \
  --wait --timeout 10m
```

### 5.3 Verify Deployment

```bash
# Check pods
kubectl get pods -n solace

# Expected output:
# NAME                                    READY   STATUS    RESTARTS   AGE
# schema-registry-backend-0               1/1     Running   0          2m
# schema-registry-backend-1               1/1     Running   0          2m
# schema-registry-backend-2               1/1     Running   0          2m
# schema-registry-db-cluster-1            1/1     Running   0          3m
# schema-registry-idp-0                   1/1     Running   0          2m
# schema-registry-ui-0                    1/1     Running   0          2m

# Check services
kubectl get svc -n solace

# Check ingress
kubectl get ingress -n solace
```

## Step 6: Access Schema Registry

### 6.1 Get URLs

```bash
# APIs endpoint
echo "APIs: https://apis.$LB_IP.nip.io"

# Web UI endpoint
echo "Web UI: https://ui.$LB_IP.nip.io"
```

### 6.2 Test API

```bash
# Test system info endpoint (use -k for self-signed cert)
curl -k -u developer:YOUR_PASSWORD https://apis.$LB_IP.nip.io/system/info

# Expected output:
{
  "name": "Solace Schema Registry",
  "version": "1.0.0",
  ...
}
```

### 6.3 Access Web UI

1. Open browser to `https://ui.YOUR_LB_IP.nip.io`
2. Accept the self-signed certificate warning
3. Login with credentials from `values-override.yaml`:
   - **Developer**: username=`developer`, password from `idp.developerPassword`
   - **Read-only**: username=`readonly`, password from `idp.readonlyPassword`

## Step 7: Register Schema

### 7.1 Via REST API

```bash
# Create schema artifact
curl -k -X POST \
  -u developer:YOUR_PASSWORD \
  -H "Content-Type: application/json" \
  -H "X-Registry-ArtifactId: solace/samples/goodschema" \
  https://apis.$LB_IP.nip.io/apis/registry/v3/groups/default/artifacts \
  -d '{
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
      "name": { "type": "string" },
      "id": { "type": "string" },
      "email": { "type": "string", "format": "email" }
    },
    "required": ["name", "id", "email"]
  }'
```

### 7.2 Via Web UI

1. Navigate to **Artifacts** tab
2. Click **Create Artifact**
3. Enter:
   - Group: `default`
   - Artifact ID: `solace/samples/goodschema`
   - Type: `JSON`
4. Paste schema JSON
5. Click **Create**

## Troubleshooting

### Pods Not Starting

```bash
# Check pod logs
kubectl logs -n solace <pod-name>

# Check pod events
kubectl describe pod -n solace <pod-name>

# Common issues:
# - Image pull errors: Check ECR credentials and image names
# - Database connection: Verify PostgreSQL cluster is healthy
# - Resource constraints: Check node capacity
```

### Certificate Warnings

For development, self-signed certificates are OK. For production:
1. Use Let's Encrypt with cert-manager
2. Or upload CA-signed certificates

### Can't Access Ingress

```bash
# Check load balancer status
kubectl get svc -n ingress-nginx ingress-nginx-controller

# Check security groups allow ports 80/443
# Check DNS resolves correctly
nslookup apis.$LB_IP.nip.io
```

### Image Pull Failures

```bash
# Refresh ECR credentials (they expire after 12 hours)
kubectl -n solace delete secret docker-registry-config

kubectl -n solace create secret docker-registry docker-registry-config \
  --docker-server=$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com \
  --docker-username=AWS \
  --docker-password=$(aws ecr get-login-password --region $AWS_REGION)
```

## Production Considerations

⚠️ **This setup is for development/testing only**. For production:

### Security
- Use CA-signed TLS certificates (Let's Encrypt, AWS ACM)
- Integrate with enterprise IDP (LDAP, Active Directory, OIDC)
- Enable RBAC and namespace isolation
- Use AWS Secrets Manager for credentials
- Restrict ingress with security groups

### High Availability
- Deploy 3+ PostgreSQL replicas
- Deploy 3+ Schema Registry backend replicas
- Use multiple availability zones
- Configure pod disruption budgets
- Set up pod anti-affinity rules

### Monitoring & Logging
- Enable Prometheus metrics
- Configure CloudWatch integration
- Set up alerting (PagerDuty, Opsgenie)
- Enable audit logging
- Monitor API latency and error rates

### Backup & Recovery
- Configure PostgreSQL backups (S3)
- Test restore procedures
- Document recovery time objectives (RTO)
- Implement disaster recovery plan

### Performance
- Use larger node instances (m5.2xlarge+)
- Increase PostgreSQL resources
- Enable connection pooling
- Configure autoscaling for Schema Registry pods

## Cleanup

To remove all resources:

```bash
# Delete Helm releases
helm uninstall schema-registry -n solace
helm uninstall ingress-nginx -n ingress-nginx
helm uninstall cnpg-operator -n cnpg-system

# Delete CloudFormation stacks
aws cloudformation delete-stack --stack-name schema-registry-ecr --region us-east-2
aws cloudformation delete-stack --stack-name solace-schema-registry-eks --region us-east-2

# Note: You may need to manually delete:
# - EBS volumes
# - Load balancers
# - Elastic IPs
```

## Resources

- [Solace Schema Registry Documentation](https://docs.solace.com/Cloud/Schema-Registry/schema-registry-overview.htm)
- [CloudNativePG Documentation](https://cloudnative-pg.io/)
- [NGINX Ingress Controller](https://kubernetes.github.io/ingress-nginx/)
- [AWS EKS Best Practices](https://aws.github.io/aws-eks-best-practices/)

## Support

For issues with this deployment:
- Check the [Troubleshooting](#troubleshooting) section
- Review pod logs: `kubectl logs -n solace <pod-name>`
- Open an issue on GitHub

