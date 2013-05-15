package distMaker;

import java.util.List;

import com.google.common.collect.Lists;

import glum.task.SilentTask;

public class LoggingTask extends SilentTask
{
   private final List<String> messages = Lists.newArrayList();

   @Override
   public void infoAppend(String aMsg)
   {
      messages.add(aMsg);
      super.infoAppend(aMsg);
   }

   @Override
   public void infoAppendln(String aMsg)
   {
      messages.add(aMsg);
      super.infoAppendln(aMsg);
   }

   @Override
   public void infoUpdate(String aMsg)
   {
      messages.add(aMsg);
      super.infoUpdate(aMsg);
   }

   List<String> getMessages()
   {
      return messages;
   }
}
