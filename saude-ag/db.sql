DROP TABLE IF EXISTS pedidos;
DROP TABLE IF EXISTS produtos;
DROP TABLE IF EXISTS clientes;

CREATE TABLE IF NOT EXISTS produtos (
id INT PRIMARY KEY,
nome VARCHAR(100),
preco DECIMAL(10,2)
);
CREATE TABLE IF NOT EXISTS clientes (
id INT PRIMARY KEY,
nome VARCHAR(100),
email VARCHAR(100)
);
CREATE TABLE IF NOT EXISTS pedidos (
id INT PRIMARY KEY,
cliente_id INT,
produto_id INT,
quantidade INT,
data DATE
);

INSERT INTO produtos (id, nome, preco) VALUES 
(1, 'Produto A', 10.00), 
(2, 'Produto B', 20.50), 
(3, 'Produto C', 15.75),
(4, 'Produto D', 8.90),
(5, 'Produto E', 12.60),
(6, 'Produto F', 22.30),
(7, 'Produto G', 18.20),
(8, 'Produto H', 30.00),
(9, 'Produto I', 25.10),
(10, 'Produto J', 9.99);

INSERT INTO clientes (id, nome, email) VALUES 
(1, 'Alice', 'alice@example.com'),
(2, 'Bob', 'bob@example.com'),
(3, 'Carlos', 'carlos@example.com'),
(4, 'Diana', 'diana@example.com'),
(5, 'Eduardo', 'edu@example.com'),
(6, 'Fernanda', 'fer@example.com'),
(7, 'Gustavo', 'gus@example.com'),
(8, 'Helena', 'helena@example.com'),
(9, 'Igor', 'igor@example.com'),
(10, 'Joana', 'joana@example.com');

INSERT INTO pedidos (id, cliente_id, produto_id, quantidade, data) VALUES 
(1, 1, 2, 1, CURRENT_DATE),
(2, 2, 3, 2, CURRENT_DATE),
(3, 3, 5, 1, CURRENT_DATE),
(4, 4, 1, 3, CURRENT_DATE),
(5, 5, 6, 2, CURRENT_DATE),
(6, 6, 9, 1, CURRENT_DATE),
(7, 7, 4, 5, CURRENT_DATE),
(8, 8, 7, 1, CURRENT_DATE),
(9, 9, 8, 4, CURRENT_DATE),
(10, 10, 10, 2, CURRENT_DATE);