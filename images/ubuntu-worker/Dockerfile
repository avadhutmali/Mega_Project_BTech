FROM ubuntu:22.04

# Avoid prompts during apt installation
ENV DEBIAN_FRONTEND=noninteractive

# Install SSH server, Python, and basic networking tools
RUN apt-get update && apt-get install -y \
    openssh-server \
    python3 \
    python3-pip \
    curl \
    nano \
    iputils-ping \
    && rm -rf /var/lib/apt/lists/*

# Configure SSH for testing (Allow empty passwords)
RUN mkdir /var/run/sshd
RUN sed -i 's/#PermitEmptyPasswords no/PermitEmptyPasswords yes/' /etc/ssh/sshd_config
RUN sed -i 's/#PermitRootLogin prohibit-password/PermitRootLogin yes/' /etc/ssh/sshd_config

# Create a student user with NO password
RUN useradd -m -s /bin/bash student
RUN passwd -d student

# Expose SSH port
EXPOSE 22

# Start SSH daemon in the foreground so the container stays alive
CMD ["/usr/sbin/sshd", "-D"]
