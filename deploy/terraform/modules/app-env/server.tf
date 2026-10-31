# The server (plan section 7): Ubuntu 24.04 ARM, encrypted disk, IMDSv2 only, no SSH key,
# fixed Elastic IP. The start-up script installs Docker, AWS CLI, CloudWatch agent and swap.

# Latest Ubuntu 24.04 ARM image, published by Canonical.
data "aws_ssm_parameter" "ubuntu_ami" {
  name = "/aws/service/canonical/ubuntu/server/24.04/stable/current/arm64/hvm/ebs-gp3/ami-id"
}

resource "aws_instance" "server" {
  ami                    = data.aws_ssm_parameter.ubuntu_ami.insecure_value
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.main.id, aws_security_group.my_ip.id]
  iam_instance_profile   = aws_iam_instance_profile.server.name

  disable_api_termination = var.termination_protection
  monitoring              = false # basic 5-minute metrics are free

  user_data = templatefile("${path.module}/templates/user-data.sh.tftpl", {
    project             = var.project
    swap_size_gb        = var.swap_size_gb
    aws_region          = var.aws_region
    env_param_path      = local.env_param_path
    cw_config_parameter = aws_ssm_parameter.cloudwatch_agent.name
    backup_bucket       = local.backups_bucket_ref.bucket
    releases_bucket     = local.releases_bucket_ref.bucket
  })

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required" # IMDSv2 only
    http_put_response_hop_limit = 1          # containers cannot reach the server's role
  }

  credit_specification {
    cpu_credits = var.cpu_credits
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.disk_size_gb
    encrypted             = true
    delete_on_termination = true

    tags = { Name = "${local.name}-disk" }
  }

  tags = {
    Name   = "${local.name}-server"
    Backup = var.enable_snapshots ? "weekly" : "none"
  }

  lifecycle {
    # A newer Ubuntu image or start-up script must not replace a running server (and its data).
    ignore_changes = [ami, user_data]
  }

  depends_on = [aws_internet_gateway.main]
}

resource "aws_eip" "server" {
  domain = "vpc"

  tags = { Name = "${local.name}-ip" }
}

resource "aws_eip_association" "server" {
  instance_id   = aws_instance.server.id
  allocation_id = aws_eip.server.id
}
