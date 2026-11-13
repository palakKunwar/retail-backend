package com.retail.assistant.config;

import com.retail.assistant.domain.DeliveryType;
import com.retail.assistant.domain.OrderStatus;
import com.retail.assistant.domain.PromotionType;
import com.retail.assistant.entity.*;
import com.retail.assistant.repository.*;
import com.retail.assistant.util.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Deterministic synthetic data (fixed random seed): 79 products in 4 categories, sizes per type,
 * stock across 3 stores + 1 warehouse, offers, and ~30 days of order history for the manager view.
 * Stock-outs are scripted (not random) so the demo story stays stable.
 *
 * Demo anchor: SprintRun Pro, Black, size 9 — Hyderabad 4 (reorder level 3), Bengaluru 0,
 * Chennai 4, Warehouse 12, with 8 units sold from Hyderabad in the last two weeks. A single
 * home-delivery order to Hyderabad drops it to 3 and it surfaces as low stock for the assistant.
 */
@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private record T(String name, String sub, String brand, String color, double price, double rating,
                     String tags, String desc) {
    }

    private static final Map<String, String> CATEGORY = Map.ofEntries(
            Map.entry("Running Shoes", "Footwear"), Map.entry("Formal Shoes", "Footwear"),
            Map.entry("Casual Sneakers", "Footwear"), Map.entry("Sandals", "Footwear"),
            Map.entry("T-Shirts", "Apparel"), Map.entry("Track Pants", "Apparel"), Map.entry("Formal Shirts", "Apparel"),
            Map.entry("Sports Socks", "Accessories"), Map.entry("Formal Socks", "Accessories"),
            Map.entry("Belts", "Accessories"),
            Map.entry("Bags", "Bags & Gear"), Map.entry("Water Bottles", "Bags & Gear"));

    private static final List<T> CATALOGUE = List.of(
            // --- original seed products (kept first, same values) ---
            new T("SprintRun Pro", "Running Shoes", "Sprint", "Black", 4499, 4.5, "running,sports,jogging,comfortable,cushioned", "Comfortable black running shoes"),
            new T("UrbanRun Flex", "Running Shoes", "UrbanFit", "Black", 3999, 4.3, "running,sports,lightweight", "Lightweight black running shoes"),
            new T("ActiveStep Runner", "Running Shoes", "ActiveStep", "Blue", 3499, 4.2, "running,jogging,sports", "Comfortable everyday running shoes"),
            new T("ClassicBlack Oxford", "Formal Shoes", "Classic", "Black", 3799, 4.4, "formal,office,business,work,leather", "Black formal office shoes"),
            new T("OfficeWalk Premium", "Formal Shoes", "OfficeWalk", "Brown", 4299, 4.1, "formal,office,comfortable,work", "Comfortable formal office shoes"),
            new T("Active Sports Socks", "Sports Socks", "ActiveFit", "Black", 499, 4.3, "socks,running,sports,comfortable", "Comfortable sports socks for running"),
            new T("Classic Formal Socks", "Formal Socks", "Classic", "Black", 399, 4.2, "socks,formal,office,work", "Formal socks suitable for office wear"),
            // --- running ---
            new T("AeroDash Lite", "Running Shoes", "Aero", "Grey", 2999, 4.0, "running,lightweight,breathable", "Featherweight trainer with breathable mesh"),
            new T("StrideMax Cushion", "Running Shoes", "Stride", "Black", 5499, 4.6, "running,cushioned,comfortable,marathon", "Max-cushion daily trainer for long runs"),
            new T("TrailBlaze GTX", "Running Shoes", "Trailblaze", "Green", 6299, 4.4, "running,trail,waterproof,durable", "Waterproof trail running shoe with lugged sole"),
            new T("PacePro Elite", "Running Shoes", "PacePro", "White", 7499, 4.7, "running,premium,racing,lightweight", "Carbon-plated racing shoe"),
            new T("QuickStep Daily", "Running Shoes", "QuickStep", "Black", 2499, 3.9, "running,budget,jogging", "Affordable everyday jogging shoe"),
            new T("VeloRun Air", "Running Shoes", "Velo", "Red", 4799, 4.3, "running,breathable,sports", "Airy knit upper for warm-weather runs"),
            new T("MarathonX 2", "Running Shoes", "MarathonX", "Navy", 5999, 4.5, "running,cushioned,marathon,durable", "Long-distance stability shoe"),
            new T("FlexiRun Knit", "Running Shoes", "Flexi", "Black", 4999, 4.2, "running,lightweight,comfortable", "Sock-like knit running shoe"),
            new T("UrbanRun Flex White", "Running Shoes", "UrbanFit", "White", 3999, 4.3, "running,sports,lightweight", "Lightweight white running shoes"),
            // --- formal ---
            new T("Executive Derby", "Formal Shoes", "Regal", "Black", 4999, 4.5, "formal,office,leather,business", "Full-grain leather derby"),
            new T("Boardroom Brogue", "Formal Shoes", "Regal", "Brown", 5799, 4.4, "formal,leather,premium,business", "Hand-finished brogue"),
            new T("ComfortFormal Slip-On", "Formal Shoes", "OfficeWalk", "Black", 2999, 4.2, "formal,office,comfortable,slip-on,cushioned", "Cushioned slip-on for long workdays"),
            new T("Metro Loafer", "Formal Shoes", "Metro", "Brown", 3499, 4.1, "formal,loafer,slip-on", "Tan suede loafer"),
            new T("Heritage Monk Strap", "Formal Shoes", "Heritage", "Black", 6499, 4.6, "formal,premium,leather", "Double monk strap in polished leather"),
            new T("WorkEase Lace-Up", "Formal Shoes", "WorkEase", "Black", 2499, 4.0, "formal,office,comfortable,cushioned,work", "Budget-friendly comfortable office shoe"),
            new T("CityFormal Oxford", "Formal Shoes", "Metro", "Brown", 3299, 4.0, "formal,office,work", "Classic brown oxford"),
            new T("PrimeLeather Oxford", "Formal Shoes", "Prime", "Black", 7999, 4.8, "formal,premium,leather,business", "Goodyear-welted premium oxford"),
            // --- sneakers ---
            new T("Street Classic Low", "Casual Sneakers", "Urban", "White", 2799, 4.3, "casual,street,everyday", "Clean white low-top"),
            new T("Canvas Club", "Casual Sneakers", "Canvas Co", "Navy", 1499, 4.0, "casual,canvas,lightweight", "Breezy canvas sneaker"),
            new T("Retro Court", "Casual Sneakers", "RetroKicks", "White", 3299, 4.4, "casual,retro,leather", "Retro tennis-court sneaker"),
            new T("Skate Edge", "Casual Sneakers", "Edge", "Black", 2299, 4.1, "casual,street,durable", "Durable skate shoe"),
            new T("Weekend Slip-On", "Casual Sneakers", "Canvas Co", "Grey", 1299, 3.9, "casual,slip-on,comfortable", "Easy slip-on for weekends"),
            new T("High Top Vibe", "Casual Sneakers", "RetroKicks", "Red", 3599, 4.2, "casual,street,high-top", "Bold red high-top"),
            new T("Daily Knit Sneaker", "Casual Sneakers", "Flexi", "Black", 2599, 4.3, "casual,lightweight,comfortable,everyday", "Knit everyday sneaker"),
            new T("Minimal Leather Sneaker", "Casual Sneakers", "Prime", "White", 4499, 4.6, "casual,leather,premium", "Minimal premium leather sneaker"),
            // --- sandals ---
            new T("Coastal Flip Flop", "Sandals", "Coastal", "Blue", 499, 3.8, "beach,casual,lightweight", "Classic beach flip flop"),
            new T("Trek Floater", "Sandals", "Trailblaze", "Black", 1499, 4.2, "outdoor,durable,waterproof", "Strapped floater for treks"),
            new T("Comfort Slide", "Sandals", "Stride", "Grey", 899, 4.1, "comfortable,cushioned,casual", "Cushioned recovery slide"),
            new T("Leather Kolhapuri", "Sandals", "Heritage", "Brown", 1299, 4.4, "leather,ethnic,handcrafted", "Handcrafted leather kolhapuri"),
            new T("Sport Sandal Pro", "Sandals", "Aero", "Black", 1999, 4.3, "sports,outdoor,comfortable", "Adjustable sport sandal"),
            new T("Beach Walk", "Sandals", "Coastal", "Beige", 699, 3.9, "beach,lightweight", "Lightweight beach sandal"),
            // --- t-shirts ---
            new T("DryFit Run Tee", "T-Shirts", "Sprint", "Black", 999, 4.4, "running,sports,breathable,quick-dry", "Quick-dry running tee"),
            new T("DryFit Run Tee Blue", "T-Shirts", "Sprint", "Blue", 999, 4.4, "running,sports,breathable,quick-dry", "Quick-dry running tee"),
            new T("Cotton Crew Basic", "T-Shirts", "Basics", "White", 599, 4.1, "cotton,casual,everyday", "Soft cotton crew neck"),
            new T("Cotton Crew Basic Navy", "T-Shirts", "Basics", "Navy", 599, 4.1, "cotton,casual,everyday", "Soft cotton crew neck"),
            new T("Graphic Street Tee", "T-Shirts", "Urban", "Grey", 799, 4.0, "casual,street,cotton", "Printed street tee"),
            new T("Polo Classic", "T-Shirts", "Regal", "Navy", 1299, 4.3, "polo,smart-casual,cotton", "Pique cotton polo"),
            new T("Training Compression Tee", "T-Shirts", "PacePro", "Black", 1499, 4.5, "gym,training,sports,compression", "Compression training tee"),
            new T("Linen Blend Tee", "T-Shirts", "Coastal", "Beige", 1099, 4.2, "linen,summer,breathable", "Breathable linen-blend tee"),
            // --- track pants ---
            new T("Sprint Jogger", "Track Pants", "Sprint", "Black", 1499, 4.4, "running,sports,jogging,comfortable", "Tapered jogger with zip pockets"),
            new T("FlexMove Track Pant", "Track Pants", "Flexi", "Grey", 1299, 4.2, "gym,sports,stretch,comfortable", "Four-way stretch track pant"),
            new T("PacePro Training Pant", "Track Pants", "PacePro", "Navy", 1999, 4.5, "training,sports,quick-dry", "Quick-dry training pant"),
            new T("Urban Cargo Jogger", "Track Pants", "Urban", "Green", 1799, 4.1, "casual,cargo,street", "Cargo jogger"),
            new T("Basics Lounge Pant", "Track Pants", "Basics", "Grey", 899, 4.0, "casual,cotton,comfortable", "Cotton lounge pant"),
            new T("Aero Wind Pant", "Track Pants", "Aero", "Black", 2199, 4.3, "running,lightweight,waterproof", "Water-resistant running pant"),
            // --- formal shirts ---
            new T("Oxford Cotton Shirt", "Formal Shirts", "Regal", "White", 1799, 4.4, "formal,office,cotton,business", "Crisp white oxford shirt"),
            new T("Oxford Cotton Shirt Blue", "Formal Shirts", "Regal", "Blue", 1799, 4.4, "formal,office,cotton,business", "Classic blue oxford shirt"),
            new T("Slim Fit Office Shirt", "Formal Shirts", "OfficeWalk", "Black", 1499, 4.1, "formal,office,slim", "Slim fit office shirt"),
            new T("Executive Linen Shirt", "Formal Shirts", "Heritage", "Beige", 2499, 4.5, "formal,linen,premium,breathable", "Premium linen shirt"),
            new T("Wrinkle-Free Shirt", "Formal Shirts", "WorkEase", "Blue", 1999, 4.3, "formal,office,comfortable,wrinkle-free", "No-iron office shirt"),
            new T("Classic Stripe Shirt", "Formal Shirts", "Metro", "White", 1599, 4.0, "formal,office,stripes", "Fine stripe shirt"),
            // --- socks ---
            new T("Cushion Run Socks 3-Pack", "Sports Socks", "Stride", "White", 699, 4.4, "socks,running,cushioned,sports", "Cushioned running socks"),
            new T("Ankle Sport Socks", "Sports Socks", "Sprint", "Grey", 399, 4.1, "socks,sports,ankle", "Low-cut sport socks"),
            new T("Compression Run Socks", "Sports Socks", "PacePro", "Black", 899, 4.5, "socks,running,compression", "Graduated compression socks"),
            new T("Trail Wool Socks", "Sports Socks", "Trailblaze", "Green", 799, 4.3, "socks,trail,wool,durable", "Merino trail socks"),
            new T("Executive Socks 5-Pack", "Formal Socks", "Regal", "Black", 999, 4.4, "socks,formal,office", "Five pairs of formal socks"),
            new T("Bamboo Formal Socks", "Formal Socks", "Heritage", "Navy", 599, 4.3, "socks,formal,breathable", "Bamboo fibre formal socks"),
            new T("Office Cotton Socks", "Formal Socks", "OfficeWalk", "Brown", 349, 4.0, "socks,formal,cotton", "Cotton office socks"),
            // --- belts ---
            new T("Reversible Leather Belt", "Belts", "Regal", "Black", 1299, 4.4, "belt,leather,formal,reversible", "Black/brown reversible belt"),
            new T("Classic Brown Belt", "Belts", "Heritage", "Brown", 999, 4.2, "belt,leather,formal", "Classic brown leather belt"),
            new T("Executive Buckle Belt", "Belts", "Prime", "Black", 1899, 4.6, "belt,leather,premium,formal", "Premium buckle belt"),
            new T("Casual Canvas Belt", "Belts", "Canvas Co", "Navy", 599, 4.0, "belt,canvas,casual", "Canvas web belt"),
            new T("Braided Belt", "Belts", "Urban", "Brown", 799, 4.1, "belt,casual,braided", "Braided stretch belt"),
            // --- bags ---
            new T("Gym Duffel", "Bags", "Sprint", "Black", 1999, 4.3, "bag,gym,sports,duffel", "Gym duffel with shoe compartment"),
            new T("Laptop Backpack", "Bags", "WorkEase", "Grey", 2499, 4.5, "bag,office,laptop,backpack", "15.6\" laptop backpack"),
            new T("Executive Briefcase", "Bags", "Prime", "Brown", 4999, 4.6, "bag,office,leather,premium", "Leather briefcase"),
            new T("Daypack Lite", "Bags", "Aero", "Blue", 1499, 4.1, "bag,lightweight,travel", "Packable daypack"),
            new T("Trail Hydration Pack", "Bags", "Trailblaze", "Green", 2799, 4.4, "bag,trail,running,hydration", "Running hydration vest pack"),
            // --- bottles ---
            new T("Steel Sipper 750ml", "Water Bottles", "Sprint", "Black", 599, 4.3, "bottle,sports,steel", "Leak-proof steel sipper"),
            new T("Insulated Flask", "Water Bottles", "Trailblaze", "Blue", 999, 4.5, "bottle,insulated,travel", "24h insulated flask"),
            new T("Shaker Bottle", "Water Bottles", "PacePro", "Grey", 449, 4.0, "bottle,gym,shaker", "Protein shaker bottle"),
            new T("Collapsible Bottle", "Water Bottles", "Aero", "Green", 699, 4.1, "bottle,lightweight,travel", "Foldable silicone bottle"));

    @Value("${retail.seed.enabled:true}")
    private boolean enabled;

    @Bean
    CommandLineRunner loadData(ProductRepository productRepository, ProductVariantRepository variantRepository,
                               LocationRepository locationRepository, InventoryRepository inventoryRepository,
                               PromotionRepository promotionRepository, OrderRepository orderRepository,
                               TransactionTemplate tx, Clock clock) {
        return args -> {
            if (!enabled || productRepository.count() > 0) return;
            tx.executeWithoutResult(status -> seed(productRepository, variantRepository, locationRepository,
                    inventoryRepository, promotionRepository, orderRepository, clock));
        };
    }

    private void seed(ProductRepository productRepository, ProductVariantRepository variantRepository,
                      LocationRepository locationRepository, InventoryRepository inventoryRepository,
                      PromotionRepository promotionRepository, OrderRepository orderRepository, Clock clock) {
        Random rnd = new Random(42);
        LocalDateTime now = LocalDateTime.now(clock);

        // ---------------------------------------------------------------- locations
        Location hyd = locationRepository.save(loc("Hyderabad Store", "Hyderabad", "STORE", 1, true));
        Location blr = locationRepository.save(loc("Bengaluru Store", "Bengaluru", "STORE", 2, true));
        Location chn = locationRepository.save(loc("Chennai Store", "Chennai", "STORE", 3, true));
        Location wh = locationRepository.save(loc("Central Warehouse", "Hyderabad", "WAREHOUSE", 4, false));
        List<Location> locations = List.of(hyd, blr, chn, wh);

        // ---------------------------------------------------------------- products, variants, stock
        List<Product> products = new ArrayList<>();
        Map<Long, List<ProductVariant>> variantsByProduct = new HashMap<>();
        Map<String, Inventory> inventoryIndex = new HashMap<>();
        int skuSeq = 1;
        for (T t : CATALOGUE) {
            Product p = productRepository.save(Product.builder()
                    .name(t.name()).category(CATEGORY.get(t.sub())).subCategory(t.sub()).brand(t.brand())
                    .color(t.color()).price(t.price()).rating(t.rating()).description(t.desc()).tags(t.tags())
                    .imageUrl("").totalSold(0).build());
            products.add(p);

            List<ProductVariant> vs = new ArrayList<>();
            for (String size : sizesFor(t.sub())) {
                String sku = skuPrefix(t.name()) + "-" + t.color().substring(0, 3).toUpperCase(Locale.ROOT) + "-"
                        + size.replace(" ", "") + "-" + String.format("%03d", skuSeq);
                if (t == CATALOGUE.get(0) && "9".equals(size)) sku = "SRP-BLK-9";
                ProductVariant v = variantRepository.save(ProductVariant.builder()
                        .productId(p.getId()).size(size).color(t.color()).sku(sku).build());
                vs.add(v);
                for (Location l : locations) {
                    int reorder = l.isWarehouse() ? 10 : 3;
                    int qty = l.isWarehouse() ? stockWarehouse(rnd) : stockStore(rnd);
                    Inventory inv = inventoryRepository.save(Inventory.builder()
                            .variantId(v.getId()).locationId(l.getId()).availableQuantity(qty)
                            .reservedQuantity(0).reorderLevel(reorder).build());
                    inventoryIndex.put(v.getId() + ":" + l.getId(), inv);
                }
            }
            skuSeq++;
            variantsByProduct.put(p.getId(), vs);
        }

        // Demo anchor: SprintRun Pro / Black / 9
        ProductVariant anchor = variantsByProduct.get(products.get(0).getId()).stream()
                .filter(v -> "9".equals(v.getSize())).findFirst().orElseThrow();
        setQty(inventoryIndex, anchor, hyd, 4);
        setQty(inventoryIndex, anchor, blr, 0);
        setQty(inventoryIndex, anchor, chn, 4);
        setQty(inventoryIndex, anchor, wh, 12);

        // A network-wide stock-out to show in the manager view
        ProductVariant oos = variantsByProduct.get(products.get(9).getId()).stream()
                .filter(v -> "10".equals(v.getSize())).findFirst().orElseThrow();
        for (Location l : locations) setQty(inventoryIndex, oos, l, 0);

        // A few single-store stock-outs on slow movers (coverable by transfer)
        ProductVariant canvas6 = variant(variantsByProduct, products, "Canvas Club", "6");
        ProductVariant poloXl = variant(variantsByProduct, products, "Polo Classic", "XL");
        setQty(inventoryIndex, canvas6, chn, 0);
        setQty(inventoryIndex, poloXl, blr, 0);
        Set<Long> scripted = Set.of(anchor.getId(), oos.getId(), canvas6.getId(), poloXl.getId());

        // ---------------------------------------------------------------- promotions
        LocalDate today = now.toLocalDate();
        promotionRepository.saveAll(List.of(
                promo("RETAIL10", "10% off above ₹3,000", "10% discount on eligible orders above ₹3,000 (max ₹1,500).",
                        PromotionType.ORDER_PERCENT, 10, 3000, 1500.0, null, null, null, null, null, null, "ORDER"),
                promo("MEGA15", "15% off above ₹7,500", "15% off orders above ₹7,500 (max ₹2,500). Not combinable with other order-level offers.",
                        PromotionType.ORDER_PERCENT, 15, 7500, 2500.0, null, null, null, null, null, null, "ORDER"),
                promo("RUN500", "₹500 off running shoes", "Flat ₹500 off when you buy running shoes worth ₹4,000 or more.",
                        PromotionType.SUBCATEGORY_FLAT, 500, 4000, null, null, "Running Shoes", null, null, null, null, null),
                promo("COMBO20", "20% off accessories with footwear", "20% off accessories when your cart also contains footwear.",
                        PromotionType.BUNDLE_PERCENT, 20, 0, 1000.0, "Accessories", null, "Footwear", null, null, null, null),
                promo("WEEKEND5", "Weekend 5%", "Extra 5% off orders above ₹1,000 on Saturdays and Sundays.",
                        PromotionType.ORDER_PERCENT, 5, 1000, 750.0, null, null, null, null, null, "SATURDAY,SUNDAY", "ORDER"),
                promo("FESTIVE25", "Festive 25%", "25% off during the festive sale (ended).",
                        PromotionType.ORDER_PERCENT, 25, 2000, 3000.0, null, null, null,
                        today.minusDays(30), today.minusDays(8), null, "ORDER")));

        // ---------------------------------------------------------------- order history
        String[][] customers = {
                {"Rohan Mehta", "Hyderabad"}, {"Ananya Iyer", "Chennai"}, {"Vikram Rao", "Bengaluru"},
                {"Meera Krishnan", "Chennai"}, {"Arjun Reddy", "Hyderabad"}, {"Divya Menon", "Bengaluru"},
                {"Siddharth Jain", "Pune"}, {"Kavya Shetty", "Bengaluru"}, {"Rahul Verma", "Hyderabad"},
                {"Pooja Desai", "Mumbai"}};
        Map<String, Location> storeByCity = Map.of("Hyderabad", hyd, "Bengaluru", blr, "Chennai", chn);
        int seq = 1;

        // 8 units of the demo anchor sold from Hyderabad in the last two weeks
        int[] anchorDays = {12, 10, 8, 6, 4, 3, 2, 1};
        for (int d : anchorDays) {
            CustomerOrder o = historical(seq++, customers[seq % customers.length], now.minusDays(d).withHour(11),
                    OrderStatus.DELIVERED, hyd);
            addLine(o, products.get(0), anchor, 1, hyd);
            finish(o);
            orderRepository.save(o);
        }

        OrderStatus[] openStatuses = {OrderStatus.PLACED, OrderStatus.CONFIRMED, OrderStatus.CONFIRMED,
                OrderStatus.PICKING, OrderStatus.PACKED, OrderStatus.DISPATCHED};
        for (int i = 0; i < 64; i++) {
            String[] c = customers[rnd.nextInt(customers.length)];
            boolean open = i >= 58;
            OrderStatus status = open ? openStatuses[i - 58] : (rnd.nextInt(25) == 0 ? OrderStatus.CANCELLED : OrderStatus.DELIVERED);
            LocalDateTime at = open ? now.minusHours(2 + (64 - i) * 7L) : now.minusDays(1 + rnd.nextInt(30)).withHour(9 + rnd.nextInt(10));
            Location from = storeByCity.getOrDefault(c[1], rnd.nextBoolean() ? wh : hyd);
            CustomerOrder o = historical(seq++, c, at, status, from);
            int lines = 1 + rnd.nextInt(3);
            Set<Long> used = new HashSet<>();
            for (int k = 0; k < lines; k++) {
                Product p = weightedPick(products, rnd);
                if (!used.add(p.getId())) continue;
                List<ProductVariant> vs = variantsByProduct.get(p.getId());
                ProductVariant v = vs.get(rnd.nextInt(vs.size()));
                // Keep the demo anchors' history exact and the network-wide stock-out free of sales.
                if (scripted.contains(v.getId())) continue;
                int qty = rnd.nextInt(6) == 0 ? 2 : 1;
                addLine(o, p, v, qty, from);
                if (status.isOpen() && status != OrderStatus.DISPATCHED) {
                    Inventory inv = inventoryIndex.get(v.getId() + ":" + from.getId());
                    inv.setReservedQuantity(inv.getReservedQuantity() + qty);
                }
            }
            finish(o);
            orderRepository.save(o);
        }

        // totalSold from history
        Map<Long, Integer> sold = new HashMap<>();
        orderRepository.findAll().stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .flatMap(o -> o.getItems().stream())
                .forEach(i -> sold.merge(i.getProductId(), i.getQuantity(), Integer::sum));
        products.forEach(p -> p.setTotalSold(sold.getOrDefault(p.getId(), 0)));

        log.info("Retail synthetic data loaded: {} products, {} variants, {} inventory rows, {} orders",
                products.size(), variantRepository.count(), inventoryRepository.count(), orderRepository.count());
    }

    // ------------------------------------------------------------------------------------------

    private static Location loc(String name, String city, String type, int priority, boolean store) {
        return Location.builder().name(name).city(city).type(type).fulfillmentPriority(priority)
                .pickupEnabled(store).homeDeliveryEnabled(true).build();
    }

    private static List<String> sizesFor(String sub) {
        return switch (sub) {
            case "Running Shoes", "Formal Shoes", "Casual Sneakers", "Sandals" -> List.of("6", "7", "8", "9", "10", "11");
            case "T-Shirts", "Track Pants", "Formal Shirts" -> List.of("S", "M", "L", "XL");
            case "Sports Socks", "Formal Socks" -> List.of("M", "L");
            case "Belts" -> List.of("32", "34", "36", "38");
            default -> List.of("One Size");
        };
    }

    private static String skuPrefix(String name) {
        StringBuilder sb = new StringBuilder();
        for (String w : name.split("[\\s\\-]+")) if (!w.isEmpty() && Character.isLetter(w.charAt(0))) sb.append(Character.toUpperCase(w.charAt(0)));
        return sb.length() == 0 ? "SKU" : sb.toString();
    }

    /** Mostly healthy, ~4% low (≤ reorder level 3). Stock-outs are scripted explicitly above. */
    private static int stockStore(Random r) {
        double x = r.nextDouble();
        if (x < 0.04) return 1 + r.nextInt(3);
        return 4 + r.nextInt(12);
    }

    private static int stockWarehouse(Random r) {
        double x = r.nextDouble();
        if (x < 0.02) return 3 + r.nextInt(7);
        return 12 + r.nextInt(30);
    }

    private static ProductVariant variant(Map<Long, List<ProductVariant>> byProduct, List<Product> products,
                                          String name, String size) {
        Product p = products.stream().filter(x -> x.getName().equals(name)).findFirst().orElseThrow();
        return byProduct.get(p.getId()).stream().filter(v -> v.getSize().equals(size)).findFirst().orElseThrow();
    }

    private static void setQty(Map<String, Inventory> idx, ProductVariant v, Location l, int qty) {
        idx.get(v.getId() + ":" + l.getId()).setAvailableQuantity(qty);
    }

    private static Product weightedPick(List<Product> products, Random rnd) {
        // Footwear sells somewhat more often than other categories (one re-draw).
        Product p = products.get(rnd.nextInt(products.size()));
        return "Footwear".equals(p.getCategory()) || rnd.nextBoolean() ? p : products.get(rnd.nextInt(products.size()));
    }

    private static Promotion promo(String code, String title, String desc, PromotionType type, double value,
                                   double min, Double cap, String targetCategory, String targetSub,
                                   String requiredCategory, LocalDate from, LocalDate to, String days, String group) {
        return Promotion.builder().code(code).title(title).description(desc).type(type)
                .value(BigDecimal.valueOf(value)).minAmount(BigDecimal.valueOf(min))
                .maxDiscount(cap == null ? null : BigDecimal.valueOf(cap))
                .targetCategory(targetCategory).targetSubCategory(targetSub).requiredCategory(requiredCategory)
                .validFrom(from).validTo(to).validDays(days).stackGroup(group).active(true).build();
    }

    private static CustomerOrder historical(int seq, String[] customer, LocalDateTime at, OrderStatus status,
                                            Location from) {
        boolean pickup = seq % 4 == 0 && !from.isWarehouse();
        CustomerOrder o = CustomerOrder.builder()
                .orderNumber(String.format("RIQ-%s-%05d", at.toLocalDate().toString().replace("-", "").substring(2), seq))
                .customerName(customer[0])
                .customerEmail(customer[0].toLowerCase(Locale.ROOT).replace(' ', '.') + "@example.com")
                .customerPhone("98" + String.format("%08d", 10000000 + seq * 7919 % 89999999))
                .deliveryType(pickup ? DeliveryType.STORE_PICKUP : DeliveryType.HOME_DELIVERY)
                .deliveryCity(pickup ? from.getCity() : customer[1])
                .deliveryAddress(pickup ? null : "Synthetic address, " + customer[1])
                .pickupLocationId(pickup ? from.getId() : null)
                .pickupLocationName(pickup ? from.getName() : null)
                .status(status)
                .appliedPromotions("")
                .fulfillmentSummary(from.getName())
                .fulfillmentReason("Historical synthetic order fulfilled from " + from.getName() + ".")
                .fulfillmentEvaluation("")
                .splitShipment(false)
                .createdAt(at)
                .expectedDate(at.toLocalDate().plusDays(pickup ? 0 : 2))
                .build();
        for (OrderStatus s : OrderStatus.JOURNEY) {
            if (status == OrderStatus.CANCELLED) {
                o.addHistory(OrderStatus.PLACED, at, "Order placed");
                o.addHistory(OrderStatus.CANCELLED, at.plusHours(1), "Cancelled by customer");
                break;
            }
            o.addHistory(s, at.plusHours(s.ordinal() * 3L), "Synthetic history");
            if (s == status) break;
        }
        return o;
    }

    private static void addLine(CustomerOrder o, Product p, ProductVariant v, int qty, Location from) {
        BigDecimal unit = Money.of(p.getPrice());
        o.addItem(OrderItem.builder()
                .productId(p.getId()).variantId(v.getId()).productName(p.getName()).sku(v.getSku())
                .size(v.getSize()).color(v.getColor()).category(p.getCategory()).subCategory(p.getSubCategory())
                .unitPrice(unit).quantity(qty).lineTotal(unit.multiply(BigDecimal.valueOf(qty)))
                .fulfillmentLocationId(from.getId()).fulfillmentLocationName(from.getName()).build());
    }

    private static void finish(CustomerOrder o) {
        BigDecimal subtotal = o.getItems().stream().map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fee = o.getDeliveryType() == DeliveryType.HOME_DELIVERY && subtotal.compareTo(BigDecimal.valueOf(2499)) < 0
                ? Money.of(99) : Money.ZERO;
        o.setSubtotal(Money.round(subtotal));
        o.setDiscount(Money.ZERO);
        o.setDeliveryFee(fee);
        o.setTotal(Money.round(subtotal.add(fee)));
    }
}
