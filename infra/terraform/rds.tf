resource "aws_security_group" "rds" {
  name        = "vap-rds"
  description = "Allow MySQL only from the VAP EC2/k3s host"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "MySQL from VAP application host"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.k3s.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = { Name = "vap-rds-sg" }
}

resource "random_password" "rds" {
  count            = var.rds_password == null ? 1 : 0
  length           = 32
  special          = true
  override_special = "!#$%&*+-=?@^_"
}

resource "aws_db_subnet_group" "rds" {
  name       = "vap-rds"
  subnet_ids = data.aws_subnets.default.ids
  tags       = { Name = "vap-rds-subnet-group" }
}

resource "aws_db_instance" "mysql" {
  identifier                = "vap-mysql"
  engine                    = "mysql"
  engine_version            = "8.0"
  instance_class            = var.rds_instance_class
  allocated_storage         = var.rds_storage_gb
  max_allocated_storage     = var.rds_max_storage_gb
  storage_type              = "gp3"
  storage_encrypted         = true
  db_name                   = "vapdb"
  username                  = var.rds_username
  password                  = var.rds_password != null ? var.rds_password : random_password.rds[0].result
  port                      = 3306
  db_subnet_group_name      = aws_db_subnet_group.rds.name
  vpc_security_group_ids    = [aws_security_group.rds.id]
  publicly_accessible       = false
  multi_az                  = var.rds_multi_az
  backup_retention_period   = var.rds_backup_retention_days
  backup_window             = "18:00-19:00"
  maintenance_window        = "sun:19:00-sun:20:00"
  copy_tags_to_snapshot     = true
  deletion_protection       = var.rds_deletion_protection
  skip_final_snapshot       = false
  final_snapshot_identifier = "vap-mysql-final"
  apply_immediately         = false
  tags                      = { Name = "vap-mysql" }
}
