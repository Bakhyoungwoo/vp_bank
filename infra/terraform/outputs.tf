output "instance_id" {
  value = aws_instance.vap_production.id
}

output "public_ip" {
  value = aws_eip.vap_production.public_ip
}

output "security_group_id" {
  value = aws_security_group.vap_production.id
}

output "ssh_command" {
  value = "ssh -i C:/Users/5131/Downloads/vap-production.pem ubuntu@${aws_eip.vap_production.public_ip}"
}
