create table product (
    id UUID,
    code varchar(20) not null,
    name varchar(100) not null,
    primary key (id), 
    unique(code)
);

create table billing (
    id UUID,
    id_product UUID not null,
    billing_period date not null,
    customer_number varchar(100) not null,
    amount decimal(19,2) not null,
    paid boolean not null default false,
    description varchar(200),
    primary key (id), 
    foreign key (id_product) references product(id)
);

create table payment (
    id UUID,
    id_billing UUID not null,
    transaction_time timestamp not null,
    amount decimal(19,2) not null,
    payment_references varchar(36) not null,
    primary key id,
    foreign key (id_billing) references billing(id)
);