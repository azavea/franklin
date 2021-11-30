# Use AWS CloudFormation CLI for Deployment

Deployment using [AWS CloudFormation CLI](https://docs.aws.amazon.com/cli/latest/reference/cloudformation/index.html) is based on the templates as a result of building the services with [AWS Copilot CLI](../copilot/README.md). This document shows you steps of how to use both CLIs to deploy your own Franklin APIs as a load-balanced web service without tweaking configurations in between steps

## Prerequisites
- [AWS Copilot CLI](https://aws.github.io/copilot-cli/docs/overview/)
- [AWS CLI](https://aws.amazon.com/cli/)
- A named profile configured (`aws configure --profile <your profile name>`) to specify which AWS account and region to deploy your service
- A domain name registered with Amazon Route 53 in your account

## Instructions

1. `copilot app init --domain <your registered DomainName here>`
    
    This command will ask you the application name. We will use `franklin` in this example. It is required that you have a registered domain name in Amazon Route53 before this step. After going through this tutorial, your load balanced franklin API service is going to be accessible publicly through `${ServiceName}.${EnvironmentName}.${ApplicationName}.${DomainName}`. Please refer to [here](https://aws.github.io/copilot-cli/docs/developing/domain/#how-do-i-configure-an-alias-for-my-service) if you want to configure an alias for your franklin api service. Under the hood, this step will configure the your AWS admininistration roles to enable use of AWS CloudFormation StackSets. For example, we will run `copilot app init --domain rasterfoundry.com` in this tutorial.

    ```
    Use existing application: No
    Application name: franklin
    ```

2. `copilot init`

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

3. `copilot env init`

    This will create a new environment where the serivces will live. After answering the questions as below, it will link your named profile to the application to be deployed, create the common infrastructure that's shared between the services such as a VPC, an Application Load Balancer, and an ECS Cluster etc. Under the hood, on CloudFormation, it wil create a stack for cross-regional resources to support the CodePipeline for this workspace, and a stack for the environment template for infrastructure shared among Copilot workloads.

    ```
    $ copilot env init
    Environment name: production
    Credential source: [profile default]
    Default environment configuration? Yes, use default.
    ```

4. Upload Aurora Serverless Config Template to S3

    ```
    aws s3 cp ./api.addons.stack.yml s3://path/to/your/s3/franklin-api.addons.stack.yml
    ```

5. Deploy the Franklin Production API Stack with CloudFormation

    Configure the parameters in `--parameter-overrides` as you like.

    Notes:
    - the `<S3 URL to the Aurora Serverless Templat>` in the following command should be the "Object URL" from step 4, e.g.: `https://franklin-aurora-serverless-addson-stack.s3.amazonaws.com/franklin-api.addons.stack.yml`
    - the `<Your custom DomainName>` in the following command should be the same as what you used in step one, which should be a registered Route53 domain name. For this example tutorial, we are using `rasterfoundry.com`

    ```
    $ aws cloudformation deploy \
        --template-file ./api-production.stack.yml\
        --stack-name franklin-production-api \
        --capabilities CAPABILITY_IAM \
        --parameter-overrides \
            AppName=franklin \
            EnvName=production \
            WorkloadName=api \
            ContainerImage=quay.io/azavea/franklin:latest \
            AddonsTemplateURL="<S3 URL to the Aurora Serverless Template>" \
            TaskCPU=256 \
            TaskMemory=512 \
            TaskCount=1 \
            LogRetention=30 \
            ContainerPort=9090 \
            RulePath="/" \
            HTTPSEnabled=false \
            TargetContainer=api \
            TargetPort=9090 \
            Stickiness=false \
            DomainName=<Your custom DomainName>
        --tags \
            copilot-application=franklin \
            copilot-environment=production \
            copilot-service=franklin-api
    ```