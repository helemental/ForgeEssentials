package com.forgeessentials.playerlogger.remote;

import java.util.List;

import com.forgeessentials.api.remote.FERemoteHandler;
import com.forgeessentials.api.remote.GenericRemoteHandler;
import com.forgeessentials.api.remote.RemoteRequest;
import com.forgeessentials.api.remote.RemoteResponse;
import com.forgeessentials.api.remote.RemoteSession;
import com.forgeessentials.playerlogger.ModulePlayerLogger;
import com.forgeessentials.playerlogger.entity.ActionCommand;
import com.forgeessentials.remote.RemoteMessageID;

@FERemoteHandler(id = RemoteMessageID.PL_QUERY_LOG_COMMANDS)
public class QueryCommandLog extends GenericRemoteHandler<QueryLogRequest>
{

    public QueryCommandLog()
    {
        super(ModulePlayerLogger.PERM, QueryLogRequest.class);
    }

    @Override
    protected RemoteResponse<QueryLogResponse<ActionCommand>> handleData(RemoteSession session, RemoteRequest<QueryLogRequest> request)
    {
        QueryLogRequest data = request.data == null ? new QueryLogRequest() : request.data;
        List<ActionCommand> result;
        if (data.hasArea())
            result = ModulePlayerLogger.getLogger().getLoggedCommands(data.getArea(), data.startTime, data.endTime, data.getLimit());
        else
            result = ModulePlayerLogger.getLogger().getLoggedCommands(data.getPoint(), data.startTime, data.endTime, data.getLimit());
        return new RemoteResponse<QueryLogResponse<ActionCommand>>(request, new QueryLogResponse<ActionCommand>(request.data, result));
    }

}
