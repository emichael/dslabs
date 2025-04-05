package dslabs.shardkv;

import static dslabs.shardkv.ShardStoreBaseTest.CCA;
import static dslabs.shardkv.ShardStoreBaseTest.addServers;
import static dslabs.shardkv.ShardStoreBaseTest.servers;

import dslabs.framework.Address;
import dslabs.framework.testing.LocalAddress;
import dslabs.framework.testing.StateGenerator.StateGeneratorBuilder;
import dslabs.framework.testing.Workload;
import dslabs.framework.testing.junit.Lab;
import dslabs.framework.testing.search.SearchState;
import dslabs.framework.testing.visualization.VizConfig;
import dslabs.kvstore.TransactionalKVStoreWorkload;
import dslabs.shardmaster.ShardMaster.Join;
import dslabs.shardmaster.ShardMaster.Leave;
import dslabs.shardmaster.ShardMaster.Move;
import dslabs.shardmaster.ShardMaster.ShardMasterCommand;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Lab("4")
public class ShardStoreVizConfig extends VizConfig {

  private ShardMasterCommand parse(String command, int numServersPerGroup) {
    String[] split = command.split(":", 3);
    int groupId = Integer.parseInt(split[1]);

    switch (split[0]) {
      case "JOIN":
        return new Join(groupId, servers(groupId, numServersPerGroup));
      case "LEAVE":
        return new Leave(groupId);
      case "MOVE":
        int shardNum = Integer.parseInt(split[2]);
        return new Move(groupId, shardNum);
      default:
        throw new IllegalArgumentException();
    }
  }

  private List<String> getConfigCommandsIfPresent(String[] args) {
    if (args[args.length - 1].startsWith("JOIN")
        || args[args.length - 1].startsWith("LEAVE")
        || args[args.length - 1].startsWith("MOVE")) {
      return commands(args[args.length - 1]);
    }
    return null;
  }

  @Override
  public SearchState getInitialState(String[] args) {
    int numGroups = Integer.parseInt(args[0]);
    int numServersPerGroup = Integer.parseInt(args[1]);
    int numShardMasters = Integer.parseInt(args[2]);
    int numClients = Integer.parseInt(args[3]);
    int commandStart = 4;
    int commandEnd = args.length;
    List<String> configCommands = getConfigCommandsIfPresent(args);
    if (configCommands != null) {
      commandEnd--;
    }
    if (commandEnd - commandStart != 1 && commandEnd - commandStart != numClients) {
      throw new IllegalArgumentException(
          "Please provide either a single workload for all clients or a separate workload for each client.");
    }
    List<List<String>> commands = new LinkedList<>();
    for (int i = commandStart; i < commandEnd; i++) {
      commands.add(commands(args[i]));
    }

    int numShards = 10;

    StateGeneratorBuilder builder =
        ShardStoreBaseTest.builder(numGroups, numServersPerGroup, numShardMasters, numShards);

    List<Address> clients = new LinkedList<>();
    for (int i = 1; i <= numClients; i++) {
      clients.add(new LocalAddress("client" + i));
    }

    if (commands.size() == 1) {
      builder.workloadSupplier(
          TransactionalKVStoreWorkload.builder().commandStrings(commands.get(0)).build());
    } else {
      builder.workloadSupplier(
          a ->
              TransactionalKVStoreWorkload.builder()
                  .commandStrings(commands.get(clients.indexOf(a)))
                  .build());
    }

    SearchState state = new SearchState(builder.build());
    addServers(state, numGroups, numServersPerGroup, numShardMasters);

    for (Address client : clients) {
      state.addClientWorker(client);
    }

    // Add config controller with either default joins or custom commands
    if (configCommands == null) {
      configCommands =
          IntStream.rangeClosed(1, numGroups)
              .mapToObj(i -> "JOIN:" + i)
              .collect(Collectors.toList());
    }

    state.addClientWorker(
        CCA,
        Workload.builder()
            .commands(
                configCommands.stream()
                    .map(s -> parse(s, numServersPerGroup))
                    .collect(Collectors.toList()))
            .build());

    return state;
  }
}
