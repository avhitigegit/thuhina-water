package lk.thuhina.water.sales.rules;

/** A customer's bottles of one type: held, empties waiting to be collected, owed (design 4.3 customer_bottle_balance). */
public record Bottles(int held, int toCollect, int owed) {

    public static final Bottles NONE = new Bottles(0, 0, 0);

    public Bottles plus(int heldDelta, int toCollectDelta, int owedDelta) {
        return new Bottles(held + heldDelta, toCollect + toCollectDelta, owed + owedDelta);
    }
}
