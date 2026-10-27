# AWS Learning Checklist (Floci, local)

A learning roadmap: practise AWS services for this project against [Floci](https://github.com/floci-io/floci),
a local AWS emulator on `http://localhost:4566`. Anything Floci cannot emulate realistically is in the
**Later on real AWS** section at the bottom.

**How to use:** work top to bottom. Each item lists the Floci service, where it touches this project, and a
"Done when" check. Tick the box once the check passes.

> Floci notes (from its README, verify against the version you run):
> - Any non-empty credentials are accepted; IAM policies are **not enforced** in general.
> - RDS, ElastiCache, MSK, ECS and EKS start **real Docker containers**. Mount the Docker socket into Floci.
> - MSK is backed by **Redpanda** (Kafka-compatible, not Apache Kafka).
> - EKS runs **k3s**.

---

## Phase 0: Setup

- [x] **0.1 Run Floci and the AWS CLI**
  - Add Floci to `docker-compose.yml` (port `4566`, Docker socket mounted).
  - Create an `aws` CLI profile `floci` (region `us-east-1`, dummy keys, `endpoint_url = http://localhost:4566`).
  - Done when: `aws --profile floci sts get-caller-identity` returns an account ID.
- [x] **0.2 Terraform skeleton**
  - Create `infra/terraform/` with the AWS provider pointed at Floci (endpoint overrides, `s3_use_path_style`).
  - Use a `local` / `aws` split (tfvars or workspaces) so the same code can later target real AWS.
  - Windows: add `127.0.0.1 000000000000.localhost` to the hosts file. AWS provider v6 reads S3 tags via
    S3 Control, which prefixes the account ID to the endpoint hostname.
  - Run from `infra/terraform/`:
    `terraform init`, then `terraform apply -var-file=envs/floci.tfvars`, then `terraform destroy -var-file=envs/floci.tfvars`.
  - Done when: `terraform apply` creates a test S3 bucket in Floci and `terraform destroy` removes it.

## Phase 1: Identity and configuration

- [ ] **1.1 IAM: one role per service** (IAM, STS)
  - Write a Terraform role and policy for each of the 7 services (least privilege on paper; Floci won't enforce it).
  - Done when: roles exist and `aws sts assume-role` works for one of them.
- [ ] **1.2 Secrets Manager for credentials** (Secrets Manager, KMS)
  - Move `POSTGRES_PASSWORD` and `JWT_SECRET` (currently defaults in `docker-compose.yml` and
    `api-gateway/.../application.yaml`) into Secrets Manager, encrypted with a customer-managed KMS key.
  - Load them in Spring with `spring-cloud-aws-starter-secrets-manager` (`spring.config.import`).
  - Done when: services start without the secrets in compose or YAML defaults.
- [ ] **1.3 SSM Parameter Store for non-secret config** (SSM)
  - Move settings such as rate-limiter values and `NOTIFICATION_EMAIL_FROM` into `/event-booking/<service>/...`.
  - Done when: changing a parameter and restarting the service applies the new value.

## Phase 2: Managed data services

- [ ] **2.1 RDS PostgreSQL** (RDS)
  - Create an RDS instance with Terraform and the per-service databases currently made by `docker/init-db.sql`.
  - Point the services' `SPRING_DATASOURCE_URL` at the RDS endpoint; Flyway migrations must run cleanly.
  - Done when: a booking can be created end to end with RDS in place of the `postgres-db` container.
- [ ] **2.2 ElastiCache Redis** (ElastiCache)
  - Replace the `redis` container used by the `api-gateway` `RequestRateLimiter`.
  - Done when: rate limiting still returns `429` after a burst.
- [ ] **2.3 MSK** (MSK, Redpanda-backed)
  - Replace `zookeeper` and `kafka` with an MSK cluster; create topics explicitly in Terraform instead of relying on
    `KAFKA_AUTO_CREATE_TOPICS_ENABLE`.
  - Done when: the booking → inventory → payment → notification flow works over MSK.
- [ ] **2.4 SES for email** (SES)
  - Switch `notification-service` from SMTP (`SPRING_MAIL_HOST`, port 1025) to SES: either the SES SMTP interface
    or the SES v2 SDK behind a sender interface. Verify the `from` identity, add an email template.
  - Done when: a booking confirmation email appears in Floci's SES sent-messages store.
- [ ] **2.5 S3 for event images** (S3) *(new feature)*
  - Add image upload to `event-service` using presigned URLs; add a bucket policy and lifecycle rule in Terraform.
  - Done when: upload via presigned URL, then fetch the image via presigned GET.

## Phase 3: Containers and deployment

- [ ] **3.1 ECR** (ECR)
  - One repository per service; update `k8s/build-images.ps1` (or a new script) to tag and `docker push` to Floci ECR.
  - Done when: `aws ecr list-images` shows all 8 images.
- [ ] **3.2 ECS** (ECS, ELB v2, Cloud Map)
  - Task definitions and services for each app; Cloud Map for service discovery; an ALB/target group for `api-gateway`.
  - Learning point: compare Cloud Map with Eureka (`service-registry`).
  - Done when: the app runs on Floci ECS and responds through the gateway.
- [ ] **3.3 EKS** (EKS, k3s-backed)
  - Create a cluster with Terraform, `aws eks update-kubeconfig`, deploy `k8s/overlays` with a new `floci` overlay
    that uses ECR images and the Floci RDS/ElastiCache/MSK endpoints.
  - Learning point: with Kubernetes Services, `service-registry` (Eureka) becomes redundant.
  - Done when: `kubectl get pods` shows every service running and the gateway is reachable.

## Phase 4: Observability

- [ ] **4.1 CloudWatch Logs** (CloudWatch Logs)
  - Ship service logs to one log group per service (`awslogs` driver on ECS, or the CloudWatch appender).
  - Done when: `aws logs filter-log-events` finds a booking ID across services.
- [ ] **4.2 CloudWatch metrics and alarms** (CloudWatch Metrics)
  - Publish Micrometer metrics (`micrometer-registry-cloudwatch2`), e.g. payment failures, and create an alarm.
  - Done when: forcing payment failures moves the alarm to `ALARM`.
- [ ] **4.3 Managed Prometheus** (AMP) *(optional)*
  - Remote-write Actuator `/actuator/prometheus` metrics into an AMP workspace.

## Phase 5: Messaging and events (extend the design)

- [ ] **5.1 SQS dead-letter queue for failed emails** (SQS)
  - Replace or complement the in-DB email retry (`max-attempts`, `retry-interval-ms`) with SQS plus a DLQ.
  - Done when: a permanently failing email lands in the DLQ after N receives.
- [ ] **5.2 SNS fan-out** (SNS, SQS)
  - Publish "booking confirmed" to an SNS topic with SQS subscribers; compare with Kafka.
- [ ] **5.3 EventBridge Scheduler + Lambda for reminders** (EventBridge Scheduler, Lambda)
  - Schedule a Lambda that sends event reminders 24 h before start.
  - Done when: a scheduled invocation shows up in the Lambda's CloudWatch logs.
- [ ] **5.4 Step Functions saga** (Step Functions) *(advanced)*
  - Model the booking → payment → compensation flow as a state machine, to compare with the current Kafka choreography.

## Phase 6: API, security and CI/CD

- [ ] **6.1 Cognito** (Cognito)
  - User pool plus app client; have `api-gateway` validate Cognito-issued JWTs as an alternative to `user-service` tokens.
- [ ] **6.2 WAF v2 and ACM** (WAF v2, ACM)
  - Web ACL with a rate-based rule attached to the ALB; certificate in ACM. *(Control-plane practice only.)*
- [ ] **6.3 CI/CD with Floci** (CodeBuild / CodePipeline, or GitHub Actions)
  - Pipeline: build → test → push to ECR → deploy to ECS/EKS. Run Floci as a service container in GitHub Actions
    for integration tests.
- [ ] **6.4 CloudFormation or CDK comparison** (CloudFormation)
  - Rewrite one Phase 2 module in CDK or CloudFormation to compare with Terraform.
- [ ] **6.5 Budgets** (Budgets, Cost Explorer)
  - Define a budget and alert in Terraform so it is ready for real AWS. (Floci's cost data is synthetic.)

---

## Later on real AWS

These are things Floci cannot teach realistically:

- VPC networking: private subnets, NAT, route tables, security groups actually blocking traffic
- IAM policy evaluation and debugging real `AccessDenied` errors; IRSA / EKS Pod Identity token exchange
- Real ALB health checks, Route 53 DNS resolution, TLS with ACM on a real domain
- Apache Kafka specifics of MSK (Floci uses Redpanda), Multi-AZ RDS failover
- Real autoscaling (Application Auto Scaling policies are inert in Floci), GuardDuty findings, FIS fault injection
- Real cost and quota behaviour
