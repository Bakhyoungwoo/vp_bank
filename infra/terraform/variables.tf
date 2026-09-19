variable "aws_region" {
  description = "AWS region for the single-node k3s cluster"
  type        = string
  default     = "ap-northeast-2"
}

variable "instance_type" {
  description = "EC2 instance type; t3.large is safer for all services"
  type        = string
  default     = "t3.medium"
}

variable "admin_cidr" {
  description = "CIDR allowed to SSH to the instance and access the k3s API"
  type        = string
}

variable "key_name" {
  description = "Optional existing EC2 key pair name. Leave empty to create one."
  type        = string
  default     = ""
}

variable "root_volume_size" {
  description = "Root EBS volume size in GiB"
  type        = number
  default     = 30
}

variable "rds_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "rds_storage_gb" {
  type    = number
  default = 20
}

variable "rds_max_storage_gb" {
  type    = number
  default = 100
}

variable "rds_username" {
  type    = string
  default = "vap"
}

variable "rds_password" {
  description = "Optional RDS password; if omitted, Terraform generates one"
  type        = string
  sensitive   = true
  nullable    = true
  default     = null
}

variable "rds_multi_az" {
  type    = bool
  default = false
}

variable "rds_backup_retention_days" {
  type    = number
  default = 7
}

variable "rds_deletion_protection" {
  type    = bool
  default = true
}
