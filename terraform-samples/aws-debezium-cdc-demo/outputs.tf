output "public_ip" {
  value = aws_instance.demo.public_ip
}

output "ssh" {
  value = "ssh ec2-user@${aws_instance.demo.public_ip}"
}

output "ssh_tunnel" {
  description = "Forward app (8080) + Connect REST (8083) to your laptop."
  value       = "ssh -L 8080:localhost:8080 -L 8083:localhost:8083 ec2-user@${aws_instance.demo.public_ip}"
}

output "rsync_project" {
  description = "Copy the Spring project to the box (run from infra-demos/)."
  value       = "rsync -av --exclude target --exclude .git debezium-cdc-demo/ ec2-user@${aws_instance.demo.public_ip}:~/debezium-cdc-demo/"
}
