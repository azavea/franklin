# Use AWS Copilot CLI for Deployment

Deployment using [AWS Copilot CLI](https://aws.github.io/copilot-cli/) is a quick and straightforward way to deploy Franklin API (using the image from `quay.io/azavea/franklin:latest`) attached by an Aurora Serverless PostgreSQL DB to your AWS infrastructure. This document shows you steps of how to use this CLI to deploy your own Franklin APIs as a load-balanced web service, with some suggested configurations added in addition to the default settings. In the end, it willl provision an Application Load Balancer, security groups, an ECS service on Fargate to run `franklin-api` service, and attach Aurora Serverless PostgreSQL DB.

## Prerequisites
- [AWS Copilot CLI](https://aws.github.io/copilot-cli/docs/overview/)
- [AWS CLI](https://aws.amazon.com/cli/)
- A named profile configured (`aws configure --profile <your profile name>`) to specify which AWS account and region to deploy your service
- A domain name registered with Amazon Route 53 in your account

## Instructions
1. `copilot app init --domain <your registered DomainName here>`
    
    This command will ask you the application name. We will use `franklin` in this example. It is required that you have a registered domain name in Amazon Route53 before this step. After going through this tutorial, your load balanced franklin API service is going to be accessible publicly through `${ServiceName}.${EnvironmentName}.${ApplicationName}.${DomainName}`. Please refer to [here](https://aws.github.io/copilot-cli/docs/developing/domain/#how-do-i-configure-an-alias-for-my-service) if you want to configure an alias for your franklin api service. Under the hood, this step will configure the your AWS admininistration roles to enable use of AWS CloudFormation StackSets.

    ```
    Use existing application: No
    Application name: franklin
    ```

2. `copilot init`

    This command will ask you questions about your preferences for the application. After answering, it will initialize the infra to manage the containerized services, set up an ECR repository for the image to be uploaded, and will create a `/copilot/franklin-api/manifest.yml` file that we will configure further. Use the following answers to get started. At the end, when it asks if to deploy to a test environment, answer `No`.
    
    ```
    $ copilot init
    Use existing application: Yes // and choose franklin here
    Workload type: Load Balanced Web Service
    Service name: api
    Dockerfile: Use an existing image instead
    Image: quay.io/azavea/franklin:latest
    Port: 9090
    ```

3. Update manifest

    Add the following line to the `/copilot/franklin-api/manifest.yml` file, which is the command to run in the container using the `entrypoint` provided by the image. Please make sure that this config is a top-level configuration in `/copilot/franklin-api/manifest.yml`, e.g. a good spot to add this line should be in a new line after line `exec: true` in the manifest file. Please also replace the `<your DomainName here>` part, e.g. for this example tutorial, `rasterfoundry.com` domain name is available under my AWS account.

    ```
    command: ["serve", "--with-transactions", "--with-tiles", "--run-migrations", "--external-port", "443", "--api-scheme", "https", "--api-host", "api.production.franklin.<your DomainName here>"]
    ```

3. `copilot storage init`

    This command creates a new storage resource (Aurora Serverless in this case) attached to the `franklin-api` service. This will be accessible from inside the `franklin-api` service container through some environment variables. The command will ask you a set of questions. Use the following answers to get started. After running this command, the CLI will create an `/addons` subdirectory, which includes `CloudFormation` template for creating resources with outputs of Aurora Serverless DB with PostgreSQL engine.

    ```
    $ copilot storage init
    Only found one workload, defaulting to: api
    Storage type: Aurora Serverless
    Storage resource name: api-cluster
    Database engine: PostgreSQL
    Initial database name: franklin
    ```

4. Update addon template

    Add the following to the `Outputs:` section at the end of `/addons/franklin-api-cluster.yml`, since these will be exported and used as environment variables for the `franklin-api` service image to communicate with the Aurora Serverless DB attached.

    ```
    dbName:
        Description: "The DB_NAME exported as env var for the API container"
        Value: !Join ["", ['{{resolve:secretsmanager:', !Ref apiclusterAuroraSecret, ":SecretString:dbname}}"]]
    dbHost:
        Description: "The DB_HOST exported as env var for the API container"
        Value: !Join ["", ['{{resolve:secretsmanager:', !Ref apiclusterAuroraSecret, ":SecretString:host}}"]]
    dbUser:
        Description: "The DB_USER exported as env var for the API container"
        Value: !Join ["", ['{{resolve:secretsmanager:', !Ref apiclusterAuroraSecret, ":SecretString:username}}"]]
    dbPassword:
        Description: "The DB_PASSWORD exported as env var for the API container"
        Value: !Join ["", ['{{resolve:secretsmanager:', !Ref apiclusterAuroraSecret, ":SecretString:password}}"]]
    ```

5. `copilot env init`

    This will create a new environment where the serivces will live. After answering the questions as below, it will link your named profile to the application to be deployed, create the common infrastructure that's shared between the services such as a VPC, an Application Load Balancer, and an ECS Cluster etc. Under the hood, on CloudFormation, it wil create a stack for cross-regional resources to support the CodePipeline for this workspace, and a stack for the environment template for infrastructure shared among Copilot workloads.

    ```
    $ copilot env init
    Environment name: production
    Credential source: [profile default]
    Default environment configuration? Yes, use default.
    ```

6. `copilot deploy`

    This command will package the `manifest.yml` file and addons into `CloudFormation`, and create and/or update the ECS task definition and service. If all goes well, it should show something like the following in the end. Under the hood, this will create a stack that manages the franklin production API service, and the nested stack attached to the API service: an Aurora Serverless DB service.

    ```
    $ copilot deploy
    Only found one workload, defaulting to: franklin-api
    Only found one environment, defaulting to: production
    Environment production is already on the latest version v1.6.1, skip upgrade.
    ✔ Proposing infrastructure changes for stack franklin-production-franklin-api 
    - Creating the infrastructure for stack franklin-production-franklin-api              [create complete]  [1103.0s]
    - An Addons CloudFormation Stack for your additional AWS resources                  [create complete]  [972.5s]
        - A Secrets Manager secret to store your DB credentials                           [create complete]  [2.9s]
        - A DB parameter group for engine configuration values                            [create complete]  [300.6s]
        - A security group for your DB cluster franklinapicluster                         [create complete]  [9.1s]
        - The franklinapicluster Aurora Serverless database cluster                       [create complete]  [365.7s]
        - A security group for your workload to access the DB cluster franklinapicluster  [create complete]  [4.5s]
    - Service discovery for your services to communicate within the VPC                 [create complete]  [0.0s]
    - Update your environment's shared resources                                        [update complete]  [214.4s]
        - A security group for your load balancer allowing HTTP and HTTPS traffic         [create complete]  [3.9s]
        - An Application Load Balancer to distribute public traffic to your services      [create complete]  [182.2s]
    - An IAM Role for the Fargate agent to make AWS API calls on your behalf            [create complete]  [13.1s]
    - A CloudWatch log group to hold your service logs                                  [create complete]  [0.2s]
    - An ECS service to run and maintain your tasks in the environment cluster          [create complete]  [87.9s]
        Deployments                                                                                           
                Revision  Rollout      Desired  Running  Failed  Pending                                           
        PRIMARY  3         [completed]  1        1        0       0                                                 
    - A target group to connect the load balancer to your service                       [create complete]  [0.0s]
    - An ECS task definition to group your containers and run them on ECS               [create complete]  [3.6s]
    - An IAM role to control permissions for the containers in your tasks               [create complete]  [16.6s]
    ✔ Deployed service franklin-api.
    Recommended follow-up action:
        You can access your service at <URL> over the internet.
    ```

7. Some commands to check the service
    - `copilot svc show`: This command shows info about the deployed services, including the endpoints, capacity and related resources per environment.
    - `copilot svc status`: This command shows the health statuses, e.g. service status, task status, and related CloudWatch alarms etc.
    - `copilot svc logs`: This command shows the the logs of the deployed service.