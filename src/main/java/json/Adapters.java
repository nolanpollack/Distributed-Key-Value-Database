package json;

import com.google.gson.*;
import messages.Message;

import java.lang.reflect.Type;

public class Adapters {
    public static class MessageDeserializer implements JsonDeserializer<Message> {
        @Override
        public Message deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            String messageType = jsonElement.getAsJsonObject().get("type").getAsString();
            switch (messageType) {
                case "get":
                    return jsonDeserializationContext.deserialize(jsonElement, messages.GetMessage.class);
                case "put":
                    return jsonDeserializationContext.deserialize(jsonElement, messages.PutMessage.class);
                case "vote":
                    return jsonDeserializationContext.deserialize(jsonElement, messages.VoteMessage.class);
                case "requestVote":
                    return jsonDeserializationContext.deserialize(jsonElement, messages.RequestVoteMessage.class);
                case "appendEntries":
                    return jsonDeserializationContext.deserialize(jsonElement, messages.AppendEntriesMessage.class);
                case "appendEntriesResponse":
                    return jsonDeserializationContext.deserialize(jsonElement, messages.AppendEntriesResponseMessage.class);
                default:
                    return new Gson().fromJson(jsonElement, Message.class);
            }
        }
    }
}
