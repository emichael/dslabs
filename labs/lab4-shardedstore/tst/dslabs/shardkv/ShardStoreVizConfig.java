package dslabs.shardkv;

import dslabs.framework.testing.LocalAddress;
import dslabs.framework.testing.StateGenerator.StateGeneratorBuilder;
import dslabs.framework.testing.Workload;
import dslabs.framework.testing.search.SearchState;
import dslabs.framework.testing.visualization.VizConfig;
import dslabs.kvstore.TransactionalKVStoreWorkload;
import dslabs.shardmaster.ShardMaster.Join;
import dslabs.shardmaster.ShardMaster.Leave;
import dslabs.shardmaster.ShardMaster.Move;
import dslabs.shardmaster.ShardMaster.ShardMasterCommand;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static dslabs.shardkv.ShardStoreBaseTest.CCA;
import static dslabs.shardkv.ShardStoreBaseTest.addServers;
import static dslabs.shardkv.ShardStoreBaseTest.servers;

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

    @Override
    public SearchState getInitialState(String[] args) {
        int numGroups = Integer.parseInt(args[0]);
        int numServersPerGroup = Integer.parseInt(args[1]);
        int numShardMasters = Integer.parseInt(args[2]);
        int numClients = Integer.parseInt(args[3]);
        List<String> commands = commands(args[4]);
        List<String> configCommands = null;
        if (args.length > 5) {
            configCommands = commands(args[5]);
        }

        int numShards = 10;

        StateGeneratorBuilder builder = ShardStoreBaseTest
                .builder(numGroups, numServersPerGroup, numShardMasters,
                        numShards);
        builder.workloadSupplier(
                TransactionalKVStoreWorkload.builder().commandStrings(commands)
                                            .build());

        SearchState state = new SearchState(builder.build());
        addServers(state, numGroups, numServersPerGroup, numShardMasters);

        for (int i = 1; i <= numClients; i++) {
            state.addClientWorker(new LocalAddress("client" + i));
        }

        // Add config controller with either default joins or custom commands
        if (configCommands == null) {
            configCommands = IntStream.rangeClosed(1, numGroups)
                                      .mapToObj(i -> "JOIN:" + i)
                                      .collect(Collectors.toList());
        }

        state.addClientWorker(CCA, Workload.builder().commands(
                configCommands.stream().map(s -> parse(s, numServersPerGroup))
                              .collect(Collectors.toList())).build());

        return state;
    }
}
