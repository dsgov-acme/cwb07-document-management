FROM clamav/clamav:stable_base
RUN sed -i 's/^DatabaseMirror.*/DatabaseMirror http:\/\/clamav-mirror-service/g' /etc/clamav/freshclam.conf