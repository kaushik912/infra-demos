output "public_ip" {
  value = aws_instance.demo.public_ip
}

output "ssh" {
  value = "ssh ec2-user@${aws_instance.demo.public_ip}"
}

output "ssh_tunnel" {
  description = "Forward the app (8080) to your laptop; then open http://localhost:8080/h2-console"
  value       = "ssh -L 8080:localhost:8080 ec2-user@${aws_instance.demo.public_ip}"
}

output "rsync_project" {
  description = "Copy the Spring project to the box."
  value       = "rsync -av --exclude target --exclude .git ${var.app_source_dir}/ ec2-user@${aws_instance.demo.public_ip}:~/spring-boot-h2-sample/"
}
