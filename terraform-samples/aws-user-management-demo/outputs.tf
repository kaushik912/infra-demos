output "public_ip" {
  value = aws_instance.demo.public_ip
}

output "ssh" {
  value = "ssh ec2-user@${aws_instance.demo.public_ip}"
}

output "ssh_tunnel" {
  description = "Forward app HTTPS (8443) to your laptop; then open https://localhost:8443. Only 8443: a local app on 8080 would make the tunnel warn, and the app is HTTPS-only anyway."
  value       = "ssh -L 8443:localhost:8443 ec2-user@${aws_instance.demo.public_ip}"
}

output "rsync_project" {
  description = "Copy the app + overlay files to the box (run from infra-demos/terraform-samples/aws-user-management-demo)."
  value       = "rsync -av --exclude target --exclude .git --exclude mysql-data /home/kaush/github_projs/reference-hendisantika/user-management-web-app/ ec2-user@${aws_instance.demo.public_ip}:~/user-management-web-app/"
}

output "scp_overlay" {
  description = "Copy compose overlay + .env (secrets) onto the box next to the app."
  value       = "scp docker-compose.aws.yml .env ec2-user@${aws_instance.demo.public_ip}:~/user-management-web-app/"
}
