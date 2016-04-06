-- irc2p lua hook for use with ii
--
-- requirements:
--   mkdir -p ~/irc/
--   ii -s localhost -p 6668 -n nicknameofbot 
--   echo '/j #i2p-dev' > ~/irc/in


function ircsay(chnl, msg)
  local f = io.open(os.getenv("HOME").."/irc/localhost/"..chnl.."/in", "w")
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
   local f = io.open(os.getenv("HOME").."/irc/localhost/in", "w")
   if ( f == nil  ) then
      print("cannot talk to irc, no file")
   else
      f:write(line)
      f:write("\n")
      f:flush()
      f:close()
   end
end

channel = "#i2p-dev"

function note_netsync_start(session_id, my_role, sync_type, remote_host, remote_key, includes, excludes)
   irc("/join "..channel)
   -- wait for join
   sleep(1)
end

function note_netsync_end(session_id, status, bytes_in, bytes_out, certs_in, certs_out, revs_in, revs_out, keys_in, keys_out)
  irc("/part "..channel)
end


function note_netsync_revision_received(new_id, revision, certs, session_id)
   for key, value in pairs(certs) do
      local commiter = value.key.given_name
      ircsay(channel, string.format("commit by %s: %s", commiter ,  new_id))
   end
end
