/* Thuhina Water prototype – shared placeholder data (Sri Lankan sample data).
   This is the seed. store.js copies it into localStorage on first load and
   simulates two months of history (Aug–Sep 2026) on top of it. */
(function (global) {
  'use strict';

  var DATA = {
    company: {
      name: 'Thuhina Water (Pvt) Ltd',
      address: 'No. 48, High Level Road, Nugegoda',
      phone: '011 285 4471',
      email: 'accounts@thuhinawater.lk',
      regNo: 'PV 00231877'
    },
    // Business date for the demo. Every screen treats this as "today".
    today: '2026-10-02',
    historyStart: '2026-08-01',

    bottleTypes: [
      { id: 'B20', name: '20L Bottle', litres: 20, active: true },
      { id: 'B10', name: '10L Bottle', litres: 10, active: true },
      { id: 'B5', name: '5L Bottle', litres: 5, active: false }
    ],

    products: [
      { id: 'P01', name: 'Hot & Cold Water Dispenser (Floor)', price: 38500, cost: 31000, stock: 9, active: true },
      { id: 'P02', name: 'Table-top Dispenser (Normal & Cold)', price: 14900, cost: 11500, stock: 14, active: true },
      { id: 'P03', name: 'Bottle Stand – Steel', price: 4500, cost: 3100, stock: 22, active: true },
      { id: 'P04', name: 'Manual Bottle Pump', price: 1250, cost: 750, stock: 48, active: true },
      { id: 'P05', name: 'Rechargeable Electric Pump', price: 3900, cost: 2700, stock: 17, active: true },
      { id: 'P06', name: 'Dispenser Cleaning Kit', price: 950, cost: 520, stock: 0, active: false }
    ],

    customerTypes: [
      { id: 'Household', desc: 'Homes and apartments', active: true },
      { id: 'Shop', desc: 'Retail shops, pharmacies, salons', active: true },
      { id: 'Office', desc: 'Offices, banks, schools', active: true },
      { id: 'Factory', desc: 'Factories and large workplaces', active: true }
    ],

    areas: ['Nugegoda', 'Maharagama', 'Kottawa', 'Dehiwala', 'Mount Lavinia', 'Rajagiriya',
      'Battaramulla', 'Kotte', 'Boralesgamuwa', 'Piliyandala'],

    // Price history is the source of truth for prices (FR-07).
    // key: 'water|<bottle>|<customer type>' or 'deposit|<bottle>'
    priceHistory: [
      ['water|B20|Household', 325, '2026-01-01'], ['water|B20|Shop', 310, '2026-01-01'],
      ['water|B20|Office', 300, '2026-01-01'], ['water|B20|Factory', 285, '2026-01-01'],
      ['water|B10|Household', 185, '2026-01-01'], ['water|B10|Shop', 175, '2026-01-01'],
      ['water|B10|Office', 170, '2026-01-01'], ['water|B10|Factory', 160, '2026-01-01'],
      ['deposit|B20', 1000, '2026-01-01'], ['deposit|B10', 600, '2026-01-01'],
      ['water|B20|Household', 350, '2026-07-01', 'Factory charge increase'],
      ['water|B20|Shop', 330, '2026-07-01', 'Factory charge increase'],
      ['water|B20|Office', 320, '2026-07-01', 'Factory charge increase'],
      ['water|B20|Factory', 300, '2026-07-01', 'Factory charge increase'],
      ['water|B10|Household', 200, '2026-07-01', 'Factory charge increase'],
      ['water|B10|Shop', 190, '2026-07-01', 'Factory charge increase'],
      ['water|B10|Office', 185, '2026-07-01', 'Factory charge increase'],
      ['water|B10|Factory', 175, '2026-07-01', 'Factory charge increase'],
      ['water|B10|Factory', 180, '2026-11-01', 'Scheduled revision – approved by owner']
    ],
    // Prices that are confirmed by the client (not examples).
    confirmedPrices: ['deposit|B20'],

    oldBrands: [
      { id: 'OB1', name: 'American Water', bottle: 'B20', active: true, added: '2026-01-01', note: 'Accepted in place of the Rs. 1,000 deposit' }
    ],

    // [id, name, address, area, phone, type, deliveryDay, cycleWeeks, phaseWeeks,
    //  usual20, usual10, payment, creditLimit, termsDays, (unused), since, status, openingBalance, openingDate]
    customers: [
      ['C0001', 'W.M. Sunil Perera', '23/4, Pagoda Road', 'Nugegoda', '077 412 3381', 'Household', 'Monday', 1, 0, 2, 0, 'Cash', 0, 0, null, '2024-03-11', 'Active', 0, null],
      ['C0002', 'Kumari Jayawardena', '15, Stanley Thilakaratne Mawatha', 'Nugegoda', '071 558 9024', 'Household', 'Monday', 2, 0, 1, 1, 'Cash', 0, 0, null, '2024-05-02', 'Active', 0, null],
      ['C0003', 'Sampath Grocery', '112, High Level Road', 'Maharagama', '011 285 6672', 'Shop', 'Monday', 1, 0, 4, 0, 'Credit', 25000, 30, null, '2023-11-20', 'Active', 6200, '2026-07-24'],
      ['C0004', 'Vertex Software (Pvt) Ltd', 'Level 3, 210 Kotte Road', 'Rajagiriya', '011 452 9031', 'Office', 'Monday', 1, 0, 6, 0, 'Monthly bill', 60000, 30, 300, '2023-08-01', 'Active', 7800, '2026-07-31'],
      ['C0005', 'Mohamed Rizwan', '7/2, Dharmarama Road', 'Dehiwala', '076 330 1128', 'Household', 'Tuesday', 1, 0, 2, 0, 'Cash', 0, 0, null, '2024-01-15', 'Active', 0, null],
      ['C0006', 'S. Thevarajah', '44, Hill Street', 'Dehiwala', '077 908 4415', 'Household', 'Tuesday', 2, 1, 2, 0, 'Cash', 0, 0, null, '2024-06-19', 'Active', 0, null],
      ['C0007', 'Rathna Pharmacy', '56, Old Kesbewa Road', 'Boralesgamuwa', '011 251 7740', 'Shop', 'Tuesday', 1, 0, 3, 0, 'Credit', 20000, 30, null, '2024-02-08', 'Active', 3300, '2026-07-28'],
      ['C0008', 'Ceyknit Garments (Pvt) Ltd', 'Lot 12, BOI Zone, Horana Road', 'Piliyandala', '038 224 1180', 'Factory', 'Tuesday', 1, 0, 10, 0, 'Monthly bill', 120000, 30, 290, '2023-06-12', 'Active', 12760, '2026-07-31'],
      ['C0009', 'Nadeesha Gunawardena', '18/1, Temple Road', 'Mount Lavinia', '070 215 6634', 'Household', 'Wednesday', 1, 0, 2, 1, 'Cash', 0, 0, null, '2024-08-27', 'Active', 0, null],
      ['C0010', 'Lakwin Insurance Brokers', '2nd Floor, 77 Galle Road', 'Mount Lavinia', '011 273 9902', 'Office', 'Wednesday', 1, 0, 3, 0, 'Monthly bill', 40000, 30, null, '2024-04-03', 'Active', 4160, '2026-07-31'],
      ['C0011', 'Chaminda Wijesinghe', '9, Siripala Road', 'Mount Lavinia', '077 640 2297', 'Household', 'Wednesday', 2, 0, 2, 0, 'Cash', 0, 0, null, '2025-01-10', 'Active', 0, null],
      ['C0012', 'Hiru Salon & Spa', '201, Main Street', 'Maharagama', '075 882 1043', 'Shop', 'Wednesday', 1, 0, 2, 0, 'Credit', 12000, 14, null, '2025-03-14', 'Active', 9900, '2026-06-20'],
      ['C0013', 'Ananda Kumarasiri', '31/5, Lake Drive', 'Rajagiriya', '071 903 4472', 'Household', 'Thursday', 1, 0, 3, 0, 'Cash', 0, 0, null, '2023-12-05', 'Active', 0, null],
      ['C0014', 'Green Leaf Montessori', '5, Bandaranayake Mawatha', 'Battaramulla', '011 287 3365', 'Office', 'Thursday', 1, 0, 4, 2, 'Credit', 30000, 30, null, '2024-09-09', 'Active', 0, null],
      ['C0015', 'Fathima Nusrath', '12A, Station Road', 'Battaramulla', '076 554 0981', 'Household', 'Thursday', 2, 1, 1, 0, 'Cash', 0, 0, null, '2025-02-21', 'Active', 0, null],
      ['C0016', 'Kotte Hardware Stores', '88, Kotte Road', 'Kotte', '011 286 2219', 'Shop', 'Thursday', 1, 0, 3, 0, 'Cash', 0, 0, null, '2024-07-01', 'Active', 0, null],
      ['C0017', 'R.P. Dissanayake', '62/3, Pannipitiya Road', 'Kottawa', '077 216 8850', 'Household', 'Friday', 1, 0, 2, 0, 'Cash', 0, 0, null, '2024-02-28', 'Active', 0, null],
      ['C0018', 'Kottawa Rubber Products', 'No. 4, Industrial Estate, Makumbura', 'Kottawa', '011 278 5502', 'Factory', 'Friday', 1, 0, 8, 0, 'Monthly bill', 100000, 30, null, '2023-10-16', 'Active', 10560, '2026-07-31'],
      ['C0019', 'Sanduni Herath', '40, Hospital Road', 'Kottawa', '071 662 9013', 'Household', 'Friday', 2, 0, 2, 0, 'Cash', 0, 0, null, '2024-10-12', 'Active', 0, null],
      ['C0020', 'Dilshan Fernando', '3/7, Old Road', 'Maharagama', '078 330 2265', 'Household', 'Friday', 1, 0, 1, 1, 'Cash', 0, 0, null, '2025-04-04', 'Active', 0, null],
      ['C0021', 'New Lanka Stores', '144, Highlevel Road', 'Maharagama', '011 284 7716', 'Shop', 'Friday', 1, 0, 4, 0, 'Credit', 25000, 30, 320, '2023-09-25', 'Active', 12800, '2026-08-01'],
      ['C0022', 'Prof. K. Senanayake', '19, Jambugasmulla Mawatha', 'Nugegoda', '077 129 5508', 'Household', 'Friday', 2, 1, 2, 0, 'Credit', 10000, 30, null, '2024-03-30', 'Active', 0, null],
      ['C0023', 'Pradeep Bakers', '27, Kandawatta Road', 'Nugegoda', '072 447 1906', 'Shop', 'Saturday', 1, 0, 3, 0, 'Cash', 0, 0, null, '2024-05-17', 'Active', 0, null],
      ['C0024', 'Thilini Abeysekara', '6/1, Edirisinghe Road', 'Mount Lavinia', '076 912 3384', 'Household', 'Saturday', 1, 0, 2, 0, 'Cash', 0, 0, null, '2024-11-08', 'Active', 0, null],
      ['C0025', 'Asiri Learning Centre', '75, Piliyandala Road', 'Boralesgamuwa', '011 251 3390', 'Office', 'Saturday', 1, 0, 3, 0, 'Monthly bill', 35000, 30, null, '2025-01-06', 'Active', 3840, '2026-07-31'],
      ['C0026', 'M.A. Saman Kumara', '102, Horana Road', 'Piliyandala', '070 338 7741', 'Household', 'Saturday', 2, 0, 2, 0, 'Cash', 0, 0, null, '2024-12-02', 'Active', 0, null],
      ['C0027', 'Gamini Rajapaksha', '14/9, Kesbewa Road', 'Piliyandala', '077 581 6620', 'Household', 'Monday', 1, 0, 2, 0, 'Cash', 0, 0, null, '2025-05-19', 'Active', 0, null],
      ['C0028', 'Sri Bodhi Auto Parts', '230, Avissawella Road', 'Maharagama', '011 274 6631', 'Shop', 'Monday', 2, 1, 2, 0, 'Credit', 15000, 30, null, '2024-08-12', 'Active', 0, null],
      ['C0029', 'Malini Senaratne', '5, Gregory\'s Road', 'Kotte', '071 225 0398', 'Household', 'Tuesday', 1, 0, 1, 1, 'Cash', 0, 0, null, '2025-06-01', 'Active', 0, null],
      ['C0030', 'Nippon Lanka Logistics', 'Warehouse 3, Kelani Valley Road', 'Maharagama', '011 280 4427', 'Factory', 'Wednesday', 1, 0, 6, 0, 'Monthly bill', 80000, 30, 290, '2024-01-29', 'Active', 6960, '2026-07-31'],
      ['C0031', 'Ishara Liyanage', '33, Templers Road', 'Mount Lavinia', '075 667 2014', 'Household', 'Thursday', 1, 0, 2, 0, 'Cash', 0, 0, null, '2025-07-07', 'Active', 0, null],
      ['C0032', 'Dr. A. Ramanathan', '4, Vihara Lane', 'Dehiwala', '077 345 9902', 'Household', 'Thursday', 2, 0, 2, 0, 'Cash', 0, 0, null, '2024-04-22', 'Active', 0, null],
      ['C0033', 'Pubudu Communication', '67, Stanley Thilakaratne Mawatha', 'Nugegoda', '076 118 4423', 'Shop', 'Friday', 2, 0, 2, 0, 'Cash', 0, 0, null, '2025-02-03', 'Active', 0, null],
      ['C0034', 'Ruwanthi Karunaratne', '21, Lake Road', 'Boralesgamuwa', '071 776 3305', 'Household', 'Friday', 4, 0, 2, 0, 'Cash', 0, 0, null, '2025-08-15', 'Active', 0, null],
      ['C0035', 'Hemas Tuition Hall', '9, School Lane', 'Kottawa', '011 278 9914', 'Office', 'Saturday', 2, 0, 3, 0, 'Credit', 20000, 30, null, '2024-06-10', 'Active', 0, null],
      ['C0036', 'Janaka Weerasooriya', '17/2, Siddamulla Road', 'Piliyandala', '077 902 6618', 'Household', 'Tuesday', 1, 0, 2, 0, 'Cash', 0, 0, null, '2022-11-02', 'Inactive', 0, null],
      // Registered during the history period – first purchase is simulated on 'since'.
      ['C0037', 'Shehan Madushanka', '8, Lumbini Mawatha', 'Kottawa', '070 902 3346', 'Household', 'Friday', 1, 0, 2, 0, 'Cash', 0, 0, null, '2026-09-04', 'Active', 0, null],
      ['C0038', 'Blue Ocean Cafe', '2, Beach Road', 'Mount Lavinia', '077 330 5521', 'Shop', 'Wednesday', 1, 0, 3, 0, 'Credit', 20000, 30, null, '2026-08-19', 'Active', 0, null],
      ['C0039', 'Anoma Wickramaratne', '55/1, Kirulapone Avenue', 'Nugegoda', '071 449 0072', 'Household', 'Monday', 1, 0, 2, 0, 'Cash', 0, 0, null, '2026-09-14', 'Active', 0, null],
      // Registered today, no first purchase yet – use it to demo First Purchase.
      ['C0040', 'Priyanka Rathnayake', '11, Pragathi Mawatha', 'Maharagama', '076 205 8837', 'Household', 'Monday', 1, 0, 2, 0, 'Cash', 0, 0, null, '2026-10-02', 'Active', 0, null]
    ],

    // Agreed customer prices (FR-05, client feedback 08/10/2026): [customer, bottle, price, from, until, reason, cancelled]
    // They override the standard price list for that customer only. Standard 20L: Office 320, Shop 330, Factory 300.
    custPrices: [
      ['C0004', 'B20', 300, '2026-07-01', '', 'About 26 bottles a month – customer since 2023'],
      ['C0008', 'B20', 270, '2026-07-01', '', 'Volume price – about 43 bottles a month'],
      ['C0018', 'B20', 280, '2026-09-01', '', 'Volume price – about 35 bottles a month'],
      ['C0021', 'B20', 320, '2026-07-01', '', 'Long-standing shop (since 2023)'],
      ['C0030', 'B20', 290, '2026-07-01', '2026-12-31', 'Six-month agreement – review in December'],
      ['C0014', 'B10', 165, '2026-07-01', '', 'School – 2 × 10L every week'],
      ['C0012', 'B20', 320, '2026-07-01', '2026-08-31', 'Opening offer for two months'],
      ['C0010', 'B20', 310, '2026-11-01', '', 'New contract from November (signed 28/09/2026)']
    ],

    // Bottles held at migration (31/07/2026) when they differ from the usual quantity.
    openingHeld: { C0003: { B20: 5 }, C0004: { B20: 8 }, C0008: { B20: 12 }, C0018: { B20: 10 }, C0030: { B20: 7 }, C0014: { B20: 5, B10: 2 } },

    suppliers: [
      { id: 'S01', name: 'Lanka Polymer Containers (Pvt) Ltd', contact: 'Mr. Asanka Silva', phone: '011 223 6614', email: 'sales@lankapolymer.lk', address: 'No. 18, Ekala Industrial Estate, Ja-Ela', items: ['B20', 'B10'], terms: 30, active: true },
      { id: 'S02', name: 'Ceylon PET Industries', contact: 'Ms. Roshini de Mel', phone: '011 246 8830', email: 'orders@ceylonpet.lk', address: 'Lot 7, Biyagama Export Zone, Biyagama', items: ['B20', 'B10'], terms: 30, active: true },
      { id: 'S03', name: 'Kelani Plastics', contact: 'Mr. Nuwan Peiris', phone: '011 291 0457', email: 'kelaniplastics@gmail.com', address: '45, Kandy Road, Kelaniya', items: ['B20'], terms: 14, active: true },
      { id: 'S04', name: 'HomeCool Appliances (Pvt) Ltd', contact: 'Mr. Faiz Hameed', phone: '011 268 3375', email: 'trade@homecool.lk', address: '201, Sri Sangaraja Mawatha, Colombo 10', items: ['P01', 'P02', 'P04', 'P05'], terms: 45, active: true },
      { id: 'S05', name: 'Prime Steel Works', contact: 'Mr. Lalith Gamage', phone: '038 223 4590', email: 'primesteel@sltnet.lk', address: '12, Galle Road, Panadura', items: ['P03', 'P04'], terms: 30, active: true }
    ],

    // Filling factories (more can be added). terms = payment days.
    factories: [
      { id: 'F01', name: 'AquaSeal Bottling (Pvt) Ltd', address: 'No. 6, Kandy Road, Kadawatha', contact: 'Mr. Priyantha Jayalath', phone: '011 292 7788', email: 'dispatch@aquaseal.lk', licence: 'SLS 614 / FDA-W-2291', terms: 14, charges: { B20: 60, B10: 35 }, active: true },
      { id: 'F02', name: 'Pure Lanka Fillers', address: '23, Negombo Road, Ja-Ela', contact: 'Ms. Chathurika Perera', phone: '011 223 9045', email: 'orders@purelanka.lk', licence: 'SLS 614 / FDA-W-3105', terms: 30, charges: { B20: 62, B10: 34 }, active: true }
    ],

    stockOpening: {
      B20: { empty: 30, factory: 0, filled: 95, writtenOff: 46 },
      B10: { empty: 18, factory: 0, filled: 30, writtenOff: 7 },
      B5: { empty: 0, factory: 0, filled: 0, writtenOff: 0 }
    },
    minLevels: { B20: 150, B10: 25 },

    // Default terms printed on customer quotations (editable on each quotation).
    quoteConditions: 'Prices include delivery within our delivery areas.\nA bottle deposit applies to each new bottle given, unless an accepted old bottle is handed in.\nWater is filled and sealed by an SLS-certified filling factory.\nPrices are valid until the date shown above.',

    // Running expenses (client request 08/10/2026). More types can be added on the Expenses page.
    expenseCategories: [
      { id: 'Electricity', desc: 'CEB / LECO bill', active: true },
      { id: 'Water bill', desc: 'NWSDB bill', active: true },
      { id: 'Salaries', desc: 'Staff salaries and wages', active: true },
      { id: 'Rent', desc: 'Store and office rent', active: true },
      { id: 'Vehicle fuel', desc: 'Diesel and petrol', active: true },
      { id: 'Vehicle spare parts & repairs', desc: 'Tyres, batteries, service, repairs', active: true },
      { id: 'Telephone & internet', desc: 'Mobile, landline and internet', active: true },
      { id: 'Stationery & printing', desc: 'Bill books, paper, printing', active: true },
      { id: 'Other', desc: 'Anything else', active: true }
    ],
    // [date paid, for month, type, description, paid to, amount, method, ref, bill no.]
    expenses: [
      ['2026-08-05', '2026-07', 'Electricity', 'Store – July bill', 'CEB', 3920, 'Cash', '', 'A/C 4471230981'],
      ['2026-08-06', '2026-07', 'Water bill', 'Store – July bill', 'NWSDB', 860, 'Cash', '', '10/22/331/087'],
      ['2026-08-01', '2026-08', 'Rent', 'Store rent – August', 'Mr. S. Gunasekara', 18000, 'Bank transfer', 'TRF-770112', ''],
      ['2026-08-31', '2026-08', 'Salaries', 'Delivery helper – August', 'Staff', 46000, 'Bank transfer', 'TRF-779903', ''],
      ['2026-08-09', '2026-08', 'Vehicle fuel', 'LH-4521 – week 1–2', 'Lanka IOC Nugegoda', 8600, 'Cash', '', 'F-55102'],
      ['2026-08-23', '2026-08', 'Vehicle fuel', 'LH-4521 – week 3–4', 'Lanka IOC Nugegoda', 9250, 'Cash', '', 'F-55988'],
      ['2026-08-14', '2026-08', 'Vehicle spare parts & repairs', 'LH-4521 – brake pads and service', 'Dinesh Auto Service', 7500, 'Cash', '', 'DAS-2208'],
      ['2026-08-12', '2026-08', 'Telephone & internet', 'Mobile and internet – August', 'Dialog', 2650, 'Bank transfer', 'TRF-771540', ''],
      ['2026-08-20', '2026-08', 'Stationery & printing', 'Bill books (10 × 100)', 'Sarasavi Printers', 3500, 'Cash', '', 'SP-1189'],
      ['2026-09-04', '2026-08', 'Electricity', 'Store – August bill', 'CEB', 4180, 'Cash', '', 'A/C 4471230981'],
      ['2026-09-05', '2026-08', 'Water bill', 'Store – August bill', 'NWSDB', 910, 'Cash', '', '10/22/331/087'],
      ['2026-09-01', '2026-09', 'Rent', 'Store rent – September', 'Mr. S. Gunasekara', 18000, 'Bank transfer', 'TRF-780241', ''],
      ['2026-09-30', '2026-09', 'Salaries', 'Delivery helper – September', 'Staff', 46000, 'Bank transfer', 'TRF-788310', ''],
      ['2026-09-08', '2026-09', 'Vehicle fuel', 'LH-4521 – week 1–2', 'Lanka IOC Nugegoda', 8900, 'Cash', '', 'F-56710'],
      ['2026-09-22', '2026-09', 'Vehicle fuel', 'LH-4521 – week 3–4', 'Lanka IOC Nugegoda', 8750, 'Cash', '', 'F-57322'],
      ['2026-09-17', '2026-09', 'Vehicle spare parts & repairs', 'LH-4521 – 2 rear tyres', 'Ruwan Tyre House', 16000, 'Cheque', 'Cheque 330145', 'RTH-0917'],
      ['2026-09-12', '2026-09', 'Telephone & internet', 'Mobile and internet – September', 'Dialog', 2650, 'Bank transfer', 'TRF-781960', ''],
      ['2026-09-26', '2026-09', 'Other', 'Bottle cleaning brushes and detergent', 'Nugegoda Hardware', 3200, 'Cash', '', 'NH-7731'],
      ['2026-10-01', '2026-10', 'Rent', 'Store rent – October', 'Mr. S. Gunasekara', 18000, 'Bank transfer', 'TRF-789044', ''],
      ['2026-10-02', '2026-09', 'Electricity', 'Store – September bill', 'CEB', 4260, 'Cash', '', 'A/C 4471230981']
    ],

    users: [
      { id: 'U01', username: 'nimal.admin', name: 'Nimal Perera', role: 'Admin', phone: '077 100 2201', active: true, lastLogin: '2026-10-02' },
      { id: 'U02', username: 'dilini.admin', name: 'Dilini Wickramasinghe', role: 'Admin', phone: '071 220 4410', active: true, lastLogin: '2026-10-02' },
      { id: 'U03', username: 'shanika.acc', name: 'Shanika Fernando', role: 'Accountant', phone: '076 550 1192', active: true, lastLogin: '2026-10-01' },
      { id: 'U04', username: 'kasun.d', name: 'Kasun Jayasinghe', role: 'Delivery Staff', phone: '077 881 3320', active: true, lastLogin: '2026-10-02' },
      { id: 'U05', username: 'ruwan.d', name: 'Ruwan Bandara', role: 'Delivery Staff', phone: '070 446 7702', active: true, lastLogin: '2026-10-01' },
      { id: 'U06', username: 'chamara.d', name: 'Chamara Silva', role: 'Delivery Staff', phone: '075 993 0018', active: false, lastLogin: '2026-06-14' }
    ]
  };

  global.TW_DATA = DATA;
})(typeof window !== 'undefined' ? window : globalThis);
