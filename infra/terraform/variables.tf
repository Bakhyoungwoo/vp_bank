variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-2"
}

variable "key_name" {
  description = "Existing EC2 key pair name for vap-production.pem"
  type        = string
  default     = "vap-production"
}

variable "admin_cidr" {
  description = "Administrator public IP in CIDR notation"
  type        = string
  default     = "1.245.233.2/32"
}

variable "subnet_id" {
  description = "Optional public subnet ID; empty selects the first subnet in the default VPC"
  type        = string
  default     = ""
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t3.medium"
}

variable "root_volume_size" {
  description = "Root EBS volume size in GiB"
  type        = number
  default     = 30
}
