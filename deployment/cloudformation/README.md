# Use AWS CloudFormation CLI for Deployment

Deployment using [AWS CloudFormation CLI](https://docs.aws.amazon.com/cli/latest/reference/cloudformation/index.html) is based on the templates as a result of building the services with [AWS Copilot CLI](../copilot/README.md). This document shows you steps of how to use both CLIs to deploy your own Franklin APIs as a load-balanced web service without tweaking configurations in between steps

## Prerequisites
- [AWS Copilot CLI](https://aws.github.io/copilot-cli/docs/overview/)
- [AWS CLI](https://aws.amazon.com/cli/)
- A named profile configured (`aws configure --profile <your profile name>`) to specify which AWS account and region to deploy your service

## Instructions
1. `copilot init`

    This command will ask you questions about your preferences for the application. After answering, it will initialize the infra to manage the containerized services, set up an ECR repository for the image to be uploaded, and will create a `/copilot/franklin-api/manifest.yml` file that we will configure further. Use the following answers to get started. At the end, when it asks if to deploy to a test environment, answer `No`.
    
    ```
    $ copilot init
    Use existing application: No
    Application name: franklin
    Workload type: Load Balanced Web Service
    Service name: franklin-api
    Dockerfile: Use an existing image instead
    Image: quay.io/azavea/franklin:latest
    Port: 9090
    ```

2. `copilot env init`

    This will create a new environment where the serivces will live. After answering the questions as below, it will link your named profile to the application to be deployed, create the common infrastructure that's shared between the services such as a VPC, an Application Load Balancer, and an ECS Cluster etc.

    ```
    $ copilot env init
    Environment name: production
    Credential source: [profile default]
    Default environment configuration? Yes, use default.
    ```

3. Deploy the Franklin Production API Stack with CloudFormation

    Configure the parameters in `--parameter-overrides` as you like

    ```
    $ aws cloudformation deploy \
        --template-file ./franklin-api-production.stack.yml\
        --stack-name franklin-production-franklin-api \
        --capabilities CAPABILITY_IAM \
        --parameter-overrides \
            AppName=franklin \
            EnvName=production \
            WorkloadName=franklin-api \
            ContainerImage=quay.io/azavea/franklin:latest \
            AddonsTemplateURL="https://franklin-aurora-serverless-addson-stack.s3.amazonaws.com/franklin-api.addons.stack.yml" \
            TaskCPU=256 \
            TaskMemory=512 \
            TaskCount=1 \
            LogRetention=30 \
            ContainerPort=9090 \
            RulePath="/" \
            HTTPSEnabled=false \
            TargetContainer=franklin-api \
            TargetPort=9090 \
            Stickiness=false \
        --tags \
            copilot-application=franklin \
            copilot-environment=production \
            copilot-service=franklin-api
    ```