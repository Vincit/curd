CREATE TABLE users (
    user_id serial NOT NULL,
    username varchar(64),
    country varchar(64),
    first_name varchar(64),
    last_name varchar(64),
    CONSTRAINT users_pkey PRIMARY KEY (user_id)
);
--;;
CREATE TABLE categories (
    category_id serial NOT NULL,
    name varchar(64),
    CONSTRAINT categories_pkey PRIMARY KEY (category_id)
);
--;;
CREATE TABLE blogs (
    blog_id serial NOT NULL,
    user_id bigint NOT NULL REFERENCES users (user_id),
    text varchar(255) NOT NULL,
    category_id bigint NOT NULL REFERENCES categories (category_id),
    CONSTRAINT blogs_pkey PRIMARY KEY (blog_id)
);