# Health monitoring (plan section 11): CloudWatch agent config, alarm email topic and alarms.
#   CPU > 80 % for 15 min · memory > 85 % · disk > 80 % · status checks (with automatic reboot / recovery)

# Config for the CloudWatch agent on the server. The name must start with "AmazonCloudWatch-"
# so the AWS-managed CloudWatchAgentServerPolicy lets the server read it.
resource "aws_ssm_parameter" "cloudwatch_agent" {
  name = "AmazonCloudWatch-${local.name}"
  type = "String"

  value = jsonencode({
    agent = {
      metrics_collection_interval = 60
      run_as_user                 = "root"
    }
    metrics = {
      namespace              = "CWAgent"
      append_dimensions      = { InstanceId = "$${aws:InstanceId}" }
      aggregation_dimensions = [["InstanceId"]] # one series per server – what the alarms watch
      metrics_collected = {
        mem  = { measurement = ["mem_used_percent"] }
        disk = { measurement = ["used_percent"], resources = ["/"], drop_device = true }
      }
    }
  })
}

resource "aws_sns_topic" "alerts" {
  name = "${local.name}-alerts"
}

# The email owner must click "Confirm subscription" in the email AWS sends (plan section 4).
resource "aws_sns_topic_subscription" "alerts_email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

locals {
  alarms = {
    cpu = {
      description = "CPU above 80 % for 15 minutes"
      namespace   = "AWS/EC2"
      metric      = "CPUUtilization"
      threshold   = 80
      periods     = 3
      period      = 300
      actions     = [aws_sns_topic.alerts.arn]
    }
    memory = {
      description = "Memory above 85 % for 10 minutes"
      namespace   = "CWAgent"
      metric      = "mem_used_percent"
      threshold   = 85
      periods     = 2
      period      = 300
      actions     = [aws_sns_topic.alerts.arn]
    }
    disk = {
      description = "Disk above 80 % full"
      namespace   = "CWAgent"
      metric      = "disk_used_percent"
      threshold   = 80
      periods     = 1
      period      = 300
      actions     = [aws_sns_topic.alerts.arn]
    }
    instance-check = {
      description = "Server not responding (instance status check) – rebooted automatically"
      namespace   = "AWS/EC2"
      metric      = "StatusCheckFailed_Instance"
      threshold   = 0
      periods     = 3
      period      = 60
      actions     = [aws_sns_topic.alerts.arn, "arn:aws:automate:${var.aws_region}:ec2:reboot"]
    }
    system-check = {
      description = "AWS hardware problem (system status check) – server moved automatically"
      namespace   = "AWS/EC2"
      metric      = "StatusCheckFailed_System"
      threshold   = 0
      periods     = 2
      period      = 60
      actions     = [aws_sns_topic.alerts.arn, "arn:aws:automate:${var.aws_region}:ec2:recover"]
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "this" {
  for_each = local.alarms

  alarm_name          = "${local.name}-${each.key}"
  alarm_description   = "${local.name}: ${each.value.description}"
  namespace           = each.value.namespace
  metric_name         = each.value.metric
  dimensions          = { InstanceId = aws_instance.server.id }
  statistic           = "Maximum"
  period              = each.value.period
  evaluation_periods  = each.value.periods
  comparison_operator = "GreaterThanThreshold"
  threshold           = each.value.threshold
  treat_missing_data  = "missing"

  alarm_actions = each.value.actions
  ok_actions    = [aws_sns_topic.alerts.arn]
}
