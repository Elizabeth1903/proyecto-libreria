# Sistema de inventario y ventas para una libreria
# Se ejecuta por consola y guarda los datos en SQLite (archivo tienda.db)

import sqlite3
from datetime import datetime

conexion = sqlite3.connect("tienda.db")
cursor = conexion.cursor()


def crear_tablas():
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS productos (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            nombre TEXT NOT NULL,
            precio REAL NOT NULL,
            stock INTEGER NOT NULL
        )
    """)
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS ventas (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            id_producto INTEGER NOT NULL,
            cantidad INTEGER NOT NULL,
            total REAL NOT NULL,
            fecha TEXT NOT NULL,
            FOREIGN KEY (id_producto) REFERENCES productos(id)
        )
    """)
    conexion.commit()


# Funciones para pedir datos y que el programa no se caiga si escriben mal
def pedir_entero(mensaje):
    while True:
        try:
            numero = int(input(mensaje))
            if numero < 0:
                print("No puede ser negativo.")
            else:
                return numero
        except ValueError:
            print("Ingrese un numero entero.")


def pedir_decimal(mensaje):
    while True:
        try:
            numero = float(input(mensaje))
            if numero < 0:
                print("No puede ser negativo.")
            else:
                return numero
        except ValueError:
            print("Ingrese un numero valido.")


def registrar_producto():
    print("\n--- Registrar producto ---")
    nombre = input("Nombre: ").strip()
    if nombre == "":
        print("El nombre no puede estar vacio.")
        return
    precio = pedir_decimal("Precio: ")
    stock = pedir_entero("Stock inicial: ")

    cursor.execute(
        "INSERT INTO productos (nombre, precio, stock) VALUES (?, ?, ?)",
        (nombre, precio, stock)
    )
    conexion.commit()
    print("Producto registrado con el codigo", cursor.lastrowid)


def ver_stock():
    print("\n--- Stock de productos ---")
    cursor.execute("SELECT id, nombre, precio, stock FROM productos")
    productos = cursor.fetchall()

    if len(productos) == 0:
        print("Todavia no hay productos registrados.")
        return

    print(f"{'Cod':<5}{'Nombre':<25}{'Precio':>10}{'Stock':>8}")
    print("-" * 48)
    for p in productos:
        aviso = ""
        if p[3] <= 5:
            aviso = "  <- stock bajo"
        print(f"{p[0]:<5}{p[1]:<25}{p[2]:>10.2f}{p[3]:>8}{aviso}")


def vender():
    print("\n--- Nueva venta ---")
    ver_stock()
    codigo = pedir_entero("\nCodigo del producto: ")

    cursor.execute("SELECT nombre, precio, stock FROM productos WHERE id = ?", (codigo,))
    producto = cursor.fetchone()

    if producto is None:
        print("No existe un producto con ese codigo.")
        return

    nombre, precio, stock = producto
    cantidad = pedir_entero("Cantidad: ")

    if cantidad == 0:
        print("La cantidad debe ser mayor a 0.")
        return
    if cantidad > stock:
        print("No hay stock suficiente. Solo quedan", stock)
        return

    total = precio * cantidad
    fecha = datetime.now().strftime("%Y-%m-%d %H:%M")

    cursor.execute(
        "INSERT INTO ventas (id_producto, cantidad, total, fecha) VALUES (?, ?, ?, ?)",
        (codigo, cantidad, total, fecha)
    )
    cursor.execute("UPDATE productos SET stock = stock - ? WHERE id = ?", (cantidad, codigo))
    conexion.commit()

    print(f"Venta registrada: {cantidad} x {nombre} = S/ {total:.2f}")


def agregar_stock():
    print("\n--- Agregar stock ---")
    codigo = pedir_entero("Codigo del producto: ")
    cursor.execute("SELECT nombre FROM productos WHERE id = ?", (codigo,))
    producto = cursor.fetchone()

    if producto is None:
        print("No existe un producto con ese codigo.")
        return

    cantidad = pedir_entero("Cantidad que ingresa: ")
    cursor.execute("UPDATE productos SET stock = stock + ? WHERE id = ?", (cantidad, codigo))
    conexion.commit()
    print("Stock actualizado de", producto[0])


def ver_ventas():
    print("\n--- Historial de ventas ---")
    cursor.execute("""
        SELECT ventas.id, ventas.fecha, productos.nombre, ventas.cantidad, ventas.total
        FROM ventas
        JOIN productos ON ventas.id_producto = productos.id
        ORDER BY ventas.id
    """)
    ventas = cursor.fetchall()

    if len(ventas) == 0:
        print("Todavia no hay ventas.")
        return

    suma = 0
    print(f"{'N':<4}{'Fecha':<18}{'Producto':<25}{'Cant':>5}{'Total':>10}")
    print("-" * 62)
    for v in ventas:
        print(f"{v[0]:<4}{v[1]:<18}{v[2]:<25}{v[3]:>5}{v[4]:>10.2f}")
        suma = suma + v[4]
    print("-" * 62)
    print(f"Total vendido: S/ {suma:.2f}")


def menu():
    crear_tablas()
    while True:
        print("\n===== LIBRERIA - INVENTARIO Y VENTAS =====")
        print("1. Registrar producto")
        print("2. Vender")
        print("3. Ver stock")
        print("4. Agregar stock a un producto")
        print("5. Ver historial de ventas")
        print("6. Salir")
        opcion = input("Elija una opcion: ")

        if opcion == "1":
            registrar_producto()
        elif opcion == "2":
            vender()
        elif opcion == "3":
            ver_stock()
        elif opcion == "4":
            agregar_stock()
        elif opcion == "5":
            ver_ventas()
        elif opcion == "6":
            print("Hasta luego!")
            break
        else:
            print("Opcion no valida.")

    conexion.close()


if __name__ == "__main__":
    menu()
