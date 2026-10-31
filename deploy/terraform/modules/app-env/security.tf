# Firewall (plan section 10). Two security groups on the server:
#  - main:  HTTPS from the fixed allowed IPs, HTTP from everyone (certificate check + redirect only), no SSH
#  - my-ip: HTTPS from your current IP – Terraform creates the group but never manages its rules;
#           deploy/scripts/allow-my-ip.ps1 replaces the rule when you change network.

resource "aws_security_group" "main" {
  name        = "${local.name}-web"
  description = "${local.name}: HTTPS from allowed IPs, HTTP for certificate checks and redirect"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${local.name}-web" }
}

resource "aws_vpc_security_group_ingress_rule" "http" {
  security_group_id = aws_security_group.main.id
  description       = "HTTP - Lets Encrypt check and redirect to HTTPS only"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
}

resource "aws_vpc_security_group_ingress_rule" "https" {
  for_each = toset(var.allowed_cidrs)

  security_group_id = aws_security_group.main.id
  description       = "HTTPS from an allowed fixed IP"
  cidr_ipv4         = each.value
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.main.id
  description       = "Outbound - updates, image pulls, AWS services, Lets Encrypt"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_security_group" "my_ip" {
  name        = "${local.name}-my-ip"
  description = "${local.name}: HTTPS from the developer current IP (managed by allow-my-ip)"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${local.name}-my-ip" }

  # Rules in this group are added and removed by allow-my-ip.ps1, not by Terraform.
  lifecycle {
    ignore_changes = [ingress, egress]
  }
}
