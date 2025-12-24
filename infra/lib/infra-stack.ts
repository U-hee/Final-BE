import {
  aws_certificatemanager as acm,
  aws_cloudwatch as cloudwatch,
  aws_ec2 as ec2,
  aws_ecs as ecs,
  aws_elasticloadbalancingv2 as elbv2,
  aws_iam as iam,
  aws_rds as rds,
  aws_secretsmanager as secretsmanager,
  Duration,
  RemovalPolicy,
  Stack,
  StackProps,
  CfnParameter,
} from "aws-cdk-lib";
import { Construct } from "constructs";

// .env 파일 로드 (dotenv가 이미 설치되어 있음)
const envPath = require('path').resolve(__dirname, '..', '..', '.env');
try {
  require('dotenv').config({ path: require('path').resolve(__dirname, '..', '..', '.env') });
} catch (e) {
  // dotenv 로드 실패 시 무시 (환경변수로 직접 설정 가능)
}

export class InfraStack extends Stack {
  constructor(scope: Construct, id: string, props?: StackProps) {
    super(scope, id, props);

    const desiredCountParam = new CfnParameter(this, "EcsDesiredCount", {
      type: "Number",
      default: 1,
      description:
        "Set to 0 to keep BE tasks stopped and enter 2 or more when you want to restart them.",
    });
    const desiredCount = Math.max(0, desiredCountParam.valueAsNumber);

    const namePrefixParam = new CfnParameter(this, "ResourcePrefix", {
      type: "String",
      default: "Prod",
      description:
        "Prefix to prepend to resource names (e.g., Test, Prod) so test and production environments are clearly differentiated.",
    });
    const resourcePrefix = namePrefixParam.valueAsString;

    const backendImageUri =
      process.env.BACKEND_IMAGE_URI || this.node.tryGetContext("backendImageUri");
    if (!backendImageUri) {
      throw new Error(
        "BACKEND_IMAGE_URI must be provided via env or CDK context."
      );
    }

    // Additional environment variables for backend
    const jwtSecret =
      process.env.JWT_SECRET || this.node.tryGetContext("jwtSecret") || "default-jwt-secret-change-in-production";
    const pythonUrl =
      process.env.PYTHON_URL || this.node.tryGetContext("pythonUrl") || "";
    const frontUrl =
      process.env.FRONT_URL || this.node.tryGetContext("frontUrl") || "";
    const internalToken =
      process.env['internal.token'] || 
      process.env.INTERNAL_TOKEN || 
      this.node.tryGetContext("internalToken") || 
      "";

    // Python service image URI
    const pythonImageUri =
      process.env.PYTHON_IMAGE_URI || this.node.tryGetContext("pythonImageUri");
    if (!pythonImageUri) {
      throw new Error(
        "PYTHON_IMAGE_URI must be provided via env or CDK context."
      );
    }

    const vpc = new ec2.Vpc(this, "Vpc", {
      maxAzs: 2,
      natGateways: 1,
      subnetConfiguration: [
        {
          name: "public",
          subnetType: ec2.SubnetType.PUBLIC,
        },
        {
          name: "private",
          subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
        },
      ],
    });

    const cluster = new ecs.Cluster(this, "Cluster", {
      vpc,
      clusterName: "Cluster",
    });

    const albSG = new ec2.SecurityGroup(this, "AlbSecurityGroup", {
      vpc,
      description: "ALB security group that permits internet traffic",
      allowAllOutbound: true,
    });
    albSG.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(80), "Allow HTTP");
    albSG.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(443), "Allow HTTPS");

    const backendSG = new ec2.SecurityGroup(this, "BackendServiceSecurityGroup", {
      vpc,
      description: "Permits ALB and RDS access to the backend",
      allowAllOutbound: true,
    });
    backendSG.addIngressRule(albSG, ec2.Port.tcp(80), "ALB to BE");

    // Python service security group
    const pythonSG = new ec2.SecurityGroup(this, "PythonServiceSecurityGroup", {
      vpc,
      description: "Allows backend ECS to access Python service",
      allowAllOutbound: true,
    });
    // Backend에서 Python 서비스로 접근 허용
    pythonSG.addIngressRule(backendSG, ec2.Port.tcp(8000), "Backend to Python");

    const dbSG = new ec2.SecurityGroup(this, "DatabaseSecurityGroup", {
      vpc,
      description: "Allows backend ECS to connect to the Oracle RDS instance",
      allowAllOutbound: true,
    });
    dbSG.addIngressRule(backendSG, ec2.Port.tcp(1521), "BE to Oracle");

    const dbSecret = new secretsmanager.Secret(this, "RdsAdminSecret", {
      description: "Admin credentials for connecting to the RDS instance",
      generateSecretString: {
        secretStringTemplate: JSON.stringify({ username: "finaladmin" }),
        generateStringKey: "password",
        excludePunctuation: true,
        passwordLength: 16,
      },
    });

    const dbSubnetGroup = new rds.SubnetGroup(this, "DatabaseSubnetGroup", {
      description: "Subnet group for the private RDS deployment",
      removalPolicy: RemovalPolicy.SNAPSHOT,
      vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
    });

    const dbInstance = new rds.DatabaseInstance(this, "RdsInstance", {
      engine: rds.DatabaseInstanceEngine.oracleEe({
        version: rds.OracleEngineVersion.VER_19,
      }),
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.BURSTABLE3, ec2.InstanceSize.SMALL),
      credentials: rds.Credentials.fromSecret(dbSecret),
      multiAz: false,
      allocatedStorage: 20,
      maxAllocatedStorage: 100,
      publiclyAccessible: false,
      vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      securityGroups: [dbSG],
      databaseName: "finalbe",
      removalPolicy: RemovalPolicy.DESTROY,
      deletionProtection: false,
      subnetGroup: dbSubnetGroup,
      storageEncrypted: true,
      port: 1521,
    });

    const logPrefix = "final-be";

    // Execution Role for ECR image pull
    const executionRole = new iam.Role(this, "EcsTaskExecutionRole", {
      assumedBy: new iam.ServicePrincipal("ecs-tasks.amazonaws.com"),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName(
          "service-role/AmazonECSTaskExecutionRolePolicy"
        ),
      ],
    });
    // Grant ECR permissions
    executionRole.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: ["ecr:GetAuthorizationToken"],
        resources: ["*"],
      })
    );
    executionRole.addToPolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        actions: ["ecr:BatchCheckLayerAvailability", "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage"],
        resources: ["*"],
      })
    );

    // ============================================
    // Load Balancers (먼저 생성하여 서비스에서 참조)
    // ============================================

    // Backend ALB (외부 노출)
    const alb = new elbv2.ApplicationLoadBalancer(
      this,
      "ApplicationLoadBalancer",
      {
        vpc,
        internetFacing: true,
        securityGroup: albSG,
        loadBalancerName: "ALB",
      }
    );

    // Python service internal ALB (내부 전용)
    const pythonInternalALB = new elbv2.ApplicationLoadBalancer(
      this,
      "PythonInternalALB",
      {
        vpc,
        internetFacing: false, // Internal only
        securityGroup: pythonSG,
        loadBalancerName: "PythonInternalALB",
      }
    );

    // Python service URL (백엔드에서 사용)
    const pythonServiceUrl = pythonUrl || `http://${pythonInternalALB.loadBalancerDnsName}:8000`;

    // Backend URL for Python service to call backend APIs
    const backendUrl = `http://${alb.loadBalancerDnsName}`;

    // ACM Certificate ARN 또는 ID (환경 변수에서 필수로 받음)
    const certificateArnOrId =
      process.env.CERTIFICATE_ARN ||
      process.env.CERTIFICATE_ID ||
      this.node.tryGetContext("certificateArn") ||
      this.node.tryGetContext("certificateId");
    
    if (!certificateArnOrId) {
      throw new Error(
        "CERTIFICATE_ARN or CERTIFICATE_ID must be provided via .env file or CDK context."
      );
    }

    // 인증서 ID만 제공된 경우 ARN으로 변환, 이미 ARN인 경우 그대로 사용
    const certificateArn = certificateArnOrId.startsWith("arn:aws:acm:")
      ? certificateArnOrId
      : `arn:aws:acm:${this.region}:${this.account}:certificate/${certificateArnOrId}`;

    // ACM Certificate 참조
    const certificate = acm.Certificate.fromCertificateArn(
      this,
      "Certificate",
      certificateArn
    );

    // ============================================
    // Python Service Resources
    // ============================================

    // Python service task definition
    const pythonTask = new ecs.FargateTaskDefinition(
      this,
      "PythonTaskDef",
      {
        cpu: 512,
        memoryLimitMiB: 1024,
        executionRole: executionRole,
      }
    );

    const pythonContainer = pythonTask.addContainer("PythonContainer", {
      image: ecs.ContainerImage.fromRegistry(pythonImageUri),
      logging: ecs.LogDrivers.awsLogs({
        streamPrefix: `${logPrefix}-python`,
      }),
      environment: {
        BACKEND_URL: backendUrl,
        X_INTERNAL_TOKEN: internalToken,
      },
    });
    pythonContainer.addPortMappings({ containerPort: 8000 });

    const pythonListener = pythonInternalALB.addListener("PythonListener", {
      protocol: elbv2.ApplicationProtocol.HTTP,
      port: 8000,
      open: true,
    });

    // Python service
    const pythonService = new ecs.FargateService(this, "Python-Service", {
      cluster,
      taskDefinition: pythonTask,
      assignPublicIp: false,
      desiredCount: desiredCount,
      securityGroups: [pythonSG],
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      healthCheckGracePeriod: Duration.seconds(60),
      minHealthyPercent: 100,
      maxHealthyPercent: 200,
    });

    const pythonCfn = pythonService.node.defaultChild as ecs.CfnService;
    pythonCfn.deploymentConfiguration = {
      deploymentCircuitBreaker: {
        enable: true,
        rollback: true,
      },
      maximumPercent: 200,
      minimumHealthyPercent: 100,
    };

    // Python service target group
    const pythonTargetGroup = pythonListener.addTargets("PythonTargetGroup", {
      port: 8000,
      targets: [
        pythonService.loadBalancerTarget({
          containerName: "PythonContainer",
          containerPort: 8000,
        }),
      ],
      healthCheck: {
        path: "/health", // Python 서비스의 헬스 체크 경로 (필요시 수정)
        interval: Duration.seconds(30),
      },
    });

    // ============================================
    // Backend Service Resources
    // ============================================

    const backendTask = new ecs.FargateTaskDefinition(
      this,
      "BackendTaskDef",
      {
        cpu: 1024,
        memoryLimitMiB: 2048,
        executionRole: executionRole,
      }
    );
    const backendContainer = backendTask.addContainer("BackendContainer", {
      image: ecs.ContainerImage.fromRegistry(backendImageUri),
      logging: ecs.LogDrivers.awsLogs({
        streamPrefix: `${logPrefix}-backend`,
      }),
      environment: {
        SPRING_DATASOURCE_URL: `jdbc:oracle:thin:@${dbInstance.dbInstanceEndpointAddress}:${dbInstance.dbInstanceEndpointPort}/finalbe`,
        SPRING_DATASOURCE_USERNAME: "finaladmin",
        SPRING_DOCKER_COMPOSE_ENABLED: "false",
        SPRING_DOCKER_COMPOSE_SKIP_IN_BACKGROUND: "true",
        JWT_SECRET: jwtSecret,
        PYTHON_URL: pythonServiceUrl, // Python 서비스 내부 ALB URL 사용
        FRONT_URL: frontUrl,
        INTERNAL_TOKEN: internalToken,
      },
      secrets: {
        SPRING_DATASOURCE_PASSWORD: ecs.Secret.fromSecretsManager(dbSecret, "password"),
      },
    });
    backendContainer.addPortMappings({ containerPort: 8080 });

    const backendService = new ecs.FargateService(this, "BE-Service", {
      cluster,
      taskDefinition: backendTask,
      assignPublicIp: false,
      desiredCount,
      securityGroups: [backendSG],
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      healthCheckGracePeriod: Duration.seconds(60),
      minHealthyPercent: 100,
      maxHealthyPercent: 200,
    });
    const backendCfn = backendService.node.defaultChild as ecs.CfnService;
    backendCfn.deploymentConfiguration = {
      deploymentCircuitBreaker: {
        enable: true,
        rollback: true,
      },
      maximumPercent: 200,
      minimumHealthyPercent: 100,
    };

    // Backend Target Group (리스너와 독립적으로 생성)
    const backendTargetGroup = new elbv2.ApplicationTargetGroup(
      this,
      "BackendTargetGroup",
      {
        vpc,
        port: 8080,
        protocol: elbv2.ApplicationProtocol.HTTP,
        targetType: elbv2.TargetType.IP,
        targets: [
          backendService.loadBalancerTarget({
            containerName: "BackendContainer",
            containerPort: 8080,
          }),
        ],
        healthCheck: {
          path: "/actuator/health",
          interval: Duration.seconds(30),
          healthyHttpCodes: "200",
        },
      }
    );

    // HTTPS Listener (포트 443)
    const httpsListener = alb.addListener("HttpsListener", {
      protocol: elbv2.ApplicationProtocol.HTTPS,
      port: 443,
      certificates: [certificate],
      defaultTargetGroups: [backendTargetGroup],
      open: true,
    });

    // HTTP Listener (포트 80) - HTTPS로 리다이렉트
    const httpListener = alb.addListener("HttpListener", {
      protocol: elbv2.ApplicationProtocol.HTTP,
      port: 80,
      open: true,
    });

    // HTTP에서 HTTPS로 리다이렉트
    httpListener.addAction("RedirectToHttps", {
      action: elbv2.ListenerAction.redirect({
        protocol: "HTTPS",
        port: "443",
        permanent: true,
      }),
    });

    const backend5xxAlarm = new cloudwatch.Alarm(this, "BackendFiveXXAlarm", {
      metric: backendTargetGroup.metric("HTTPCode_Target_5XX_Count", {
        period: Duration.minutes(1),
        statistic: "sum",
      }),
      threshold: 1,
      evaluationPeriods: 1,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      alarmDescription: "API 오류가 감지되면 자동 롤백 트리거",
    });
  }
}
