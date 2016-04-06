-- irc2p lua hook for use with ii
--
-- requirements:
--   mkdir -p ~/irc/
--   ii -s localhost -p 6668 -n nicknameofbot 
--   echo '/j #i2p-dev' > ~/irc/in

function ircsay(msg)
  local f = io.open("~/irc/localhost/#i2p-dev/in", "w")
  if (f == nil) then
    print ("cannot open irc pipe")
  else
    f:write(msg)
    f:write("\n")
    f:flush()
    f:close()
  end
end

function irc(line)
   local f = io.open("~/irc/localhost/in", "w")
   if ( f == nil  ) then
      print("cannot talk to irc, no file")
   else
      f:write(line)
      f:write("\n")
      f:flush()
      f:close()
   end
end

function note_netsync_start(session_id, my_role, sync_type, remote_host, remote_key, includes, excludes)
   irc("/j #i2p-dev")
end

function note_netsync_end(session_id, status, bytes_in, bytes_out, certs_in, certs_out, revs_in, revs_out, keys_in, keys_out)
  if (revs_in > 0) then
    ircsay(string.format("monotone net sync done, %d revisions in, %d keys in, %d certs in", revs_in, keys_in, certs_in))
  end
end


function note_commit(new_id, revision, certs)
  ircsay(string.format("new commit %s:", new_id))
  ircsay(revision)
end
