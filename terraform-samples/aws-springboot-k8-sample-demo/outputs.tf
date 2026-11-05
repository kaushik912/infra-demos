output "public_ip" {
  value = aws_instance.demo.public_ip
}

output "ssh" {
  value = "ssh ec2-user@${aws_instance.demo.public_ip}"
}

output "ssh_tunnel" {
  description = "Forward the k8s NodePort (30080) to local 9090; then curl localhost:9090/test/isReady. 9090 avoids clashing with a local app on 8080."
  value       = "ssh -L 9090:localhost:30080 ec2-user@${aws_instance.demo.public_ip}"
}

output "rsync_project" {
  description = "Copy the app to the box."
  value       = "rsync -av --exclude build --exclude .gradle --exclude .git /home/kaush/github_projs/reference-hendisantika/springboot-k8-sample/ ec2-user@${aws_instance.demo.public_ip}:~/springboot-k8-sample/"
}

output "scp_overlay" {
  description = "Copy our Dockerfile + k8s manifest next to the app (the repo's own Dockerfile is broken)."
  value       = "scp Dockerfile.aws k8s.yaml ec2-user@${aws_instance.demo.public_ip}:~/springboot-k8-sample/"
}
