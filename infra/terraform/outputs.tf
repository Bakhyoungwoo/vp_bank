output "public_ip" {
  value       = aws_eip.k3s.public_ip
  description = "Elastic IP used by the ingress and SSH"
}

output "ssh_command" {
  value       = "ssh -i vap-k3s.pem ubuntu@${aws_eip.k3s.public_ip}"
  description = "SSH command after saving the generated private key"
}

output "private_key" {
  value     = var.key_name == "" ? tls_private_key.ssh[0].private_key_openssh : null
  sensitive = true
}

output "frontend_bucket" {
  value       = aws_s3_bucket.frontend.bucket
  description = "Private S3 bucket for the static frontend"
}

output "cloudfront_domain" {
  value       = aws_cloudfront_distribution.frontend.domain_name
  description = "HTTPS URL for the frontend"
}

output "cloudfront_distribution_id" {
  value       = aws_cloudfront_distribution.frontend.id
  description = "CloudFront distribution ID for cache invalidation"
}

output "alb_dns_name" {
  value       = aws_lb.app.dns_name
  description = "Application Load Balancer DNS name"
}

output "rds_endpoint" {
  value       = aws_db_instance.mysql.address
  description = "Private RDS endpoint for SPRING_DATASOURCE_URL"
}

output "rds_password" {
  value       = var.rds_password != null ? var.rds_password : random_password.rds[0].result
  sensitive   = true
  description = "Generated RDS password; use only to create the Kubernetes secret"
}
